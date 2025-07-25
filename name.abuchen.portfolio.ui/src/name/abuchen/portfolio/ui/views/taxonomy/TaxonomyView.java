package name.abuchen.portfolio.ui.views.taxonomy;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.eclipse.e4.core.di.extensions.Preference;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.online.TaxonomySource;
import name.abuchen.portfolio.snapshot.filter.ClientFilter;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.util.ClientFilterDropDown;
import name.abuchen.portfolio.ui.util.DropDown;
import name.abuchen.portfolio.ui.util.ReportingPeriodDropDown;
import name.abuchen.portfolio.ui.util.ReportingPeriodDropDown.ReportingPeriodListener;
import name.abuchen.portfolio.ui.util.SimpleAction;
import name.abuchen.portfolio.ui.views.panes.HistoricalPricesPane;
import name.abuchen.portfolio.ui.views.panes.InformationPanePage;
import name.abuchen.portfolio.ui.views.panes.SecurityEventsPane;
import name.abuchen.portfolio.ui.views.panes.SecurityPriceChartPane;
import name.abuchen.portfolio.ui.views.panes.TradesPane;
import name.abuchen.portfolio.ui.views.panes.TransactionsPane;

public class TaxonomyView extends AbstractFinanceView implements PropertyChangeListener, ReportingPeriodListener
{
    private class FilterDropDown extends DropDown implements IMenuListener
    {
        public FilterDropDown(IPreferenceStore preferenceStore)
        {
            super(Messages.SecurityFilter, Images.FILTER_OFF, SWT.NONE);
            setMenuListener(this);

            loadPreselectedFilter(preferenceStore);

            if (!model.getNodeFilters().isEmpty())
                setImage(Images.FILTER_ON);
        }

        private void loadPreselectedFilter(IPreferenceStore preferenceStore)
        {
            String prefix = TaxonomyView.class.getSimpleName() + "-" + taxonomy.getId(); //$NON-NLS-1$

            // predicates
            if (preferenceStore.getBoolean(prefix + TaxonomyModel.KEY_FILTER_NON_ZERO))
                model.getNodeFilters().add(TaxonomyModel.FILTER_NON_ZERO);

            if (preferenceStore.getBoolean(prefix + TaxonomyModel.KEY_FILTER_NOT_RETIRED))
                model.getNodeFilters().add(TaxonomyModel.FILTER_NOT_RETIRED);

            addDisposeListener(e -> {
                preferenceStore.setValue(prefix + TaxonomyModel.KEY_FILTER_NON_ZERO,
                                model.getNodeFilters().contains(TaxonomyModel.FILTER_NON_ZERO));
                preferenceStore.setValue(prefix + TaxonomyModel.KEY_FILTER_NOT_RETIRED,
                                model.getNodeFilters().contains(TaxonomyModel.FILTER_NOT_RETIRED));
            });
        }

        private void updateIcon()
        {
            boolean hasActiveFilter = !model.getNodeFilters().isEmpty();
            setImage(hasActiveFilter ? Images.FILTER_ON : Images.FILTER_OFF);
        }

        @Override
        public void menuAboutToShow(IMenuManager manager)
        {
            // show the node filter menu only for the tree pages
            getCurrentPage().filter(p -> p instanceof AbstractNodeTreeViewer).ifPresent(p -> {
                manager.add(buildNodeFilterAction(Messages.FilterValuationNonZero, TaxonomyModel.FILTER_NON_ZERO));
                manager.add(buildNodeFilterAction(Messages.FilterNotRetired, TaxonomyModel.FILTER_NOT_RETIRED));
            });
        }

        private Action buildNodeFilterAction(String label, Predicate<TaxonomyNode> predicate)
        {
            Action action = new SimpleAction(label, a -> {
                boolean isActive = model.getNodeFilters().contains(predicate);
                if (!isActive)
                    model.getNodeFilters().add(predicate);
                else
                    model.getNodeFilters().remove(predicate);

                model.fireTaxonomyModelChange(model.getVirtualRootNode());
                updateIcon();
            });

            action.setChecked(model.getNodeFilters().contains(predicate));
            return action;
        }
    }

    /** preference key: store last active view as index */
    private String identifierView;
    /** preference key: include unassigned category in charts */
    private String identifierUnassigned;
    /** preference key: exclude securities in pie chart */
    private String identifierExclucdeSecuritiesInPieChart;
    /** preference key: order by taxonomy in stack chart */
    private String identifierOrderByTaxonomy;
    /** preference key: node expansion state in definition viewer */
    private String expansionStateDefinition;
    /** preference key: node expansion state in rebalancing viewer */
    private String expansionStateReblancing;
    /** preference key: coloring strategy in the tree map */
    private String identifierColoringStrategy;
    /** preference key: show group heading in the tree map */
    private String identifierGroupHeading;
    /** preference key: color schema used in the tree map */
    private String identifierColorSchema;

    private TaxonomyModel model;
    private Taxonomy taxonomy;
    private ClientFilterDropDown clientFilterDropDown;
    private ReportingPeriodDropDown reportingPeriodDropDown;

    private Composite container;
    private List<Action> viewActions = new ArrayList<>();

    @Inject
    @Preference(UIConstants.Preferences.ENABLE_EXPERIMENTAL_FEATURES)
    boolean enableExperimentalFeatures;

    @Override
    protected String getDefaultTitle()
    {
        var title = new StringBuilder();

        title.append(taxonomy.getName());

        if (clientFilterDropDown.hasActiveFilter())
            title.append(" : ").append(clientFilterDropDown.getClientFilterMenu().getSelectedItem().getLabel()); //$NON-NLS-1$

        return title.toString();
    }

    @Inject
    public void setup(@Named(UIConstants.Parameter.VIEW_PARAMETER) Taxonomy taxonomy,
                    ExchangeRateProviderFactory factory)
    {
        this.taxonomy = taxonomy;

        this.model = new TaxonomyModel(factory, getClient(), taxonomy);

        Consumer<ClientFilter> listener = filter -> {
            setInformationPaneInput(null);
            Client filteredClient = filter.filter(getClient());
            setToContext(UIConstants.Context.FILTERED_CLIENT, filteredClient);
            model.updateClientSnapshot(filteredClient);
        };
        this.clientFilterDropDown = new ClientFilterDropDown(getClient(), getPreferenceStore(),
                        TaxonomyView.class.getSimpleName() + "-" + taxonomy.getId(), listener); //$NON-NLS-1$

        // As the taxonomy model is initially calculated in the
        // TaxonomyModel#init method, we must recalculate the values if an
        // active filter exists.
        if (this.clientFilterDropDown.hasActiveFilter())
            listener.accept(this.clientFilterDropDown.getSelectedFilter());

        this.clientFilterDropDown.getClientFilterMenu().addListener(filter -> updateTitle(getDefaultTitle()));

        this.identifierView = TaxonomyView.class.getSimpleName() + "-VIEW-" + taxonomy.getId(); //$NON-NLS-1$
        this.identifierUnassigned = TaxonomyView.class.getSimpleName() + "-UNASSIGNED-" + taxonomy.getId(); //$NON-NLS-1$
        this.identifierExclucdeSecuritiesInPieChart = TaxonomyView.class.getSimpleName() + "-EXCLUDESECURITESPIECHART-" //$NON-NLS-1$
                        + taxonomy.getId();
        this.identifierOrderByTaxonomy = TaxonomyView.class.getSimpleName() + "-ORDERBYTAXONOMY-" + taxonomy.getId(); //$NON-NLS-1$
        this.expansionStateDefinition = TaxonomyView.class.getSimpleName() + "-EXPANSION-DEFINITION-" //$NON-NLS-1$
                        + taxonomy.getId();
        this.expansionStateReblancing = TaxonomyView.class.getSimpleName() + "-EXPANSION-REBALANCE-" //$NON-NLS-1$
                        + taxonomy.getId();

        this.identifierColoringStrategy = TaxonomyView.class.getSimpleName() + "-COLORINGSTRATEGY-" + taxonomy.getId(); //$NON-NLS-1$
        this.identifierGroupHeading = TaxonomyView.class.getSimpleName() + "-GROUPHEADING-" //$NON-NLS-1$
                        + taxonomy.getId();
        this.identifierColorSchema = TaxonomyView.class.getSimpleName() + "-COLORSCHEMA-" //$NON-NLS-1$
                        + taxonomy.getId();

        IPreferenceStore preferences = getPreferenceStore();
        this.model.setExcludeUnassignedCategoryInCharts(preferences.getBoolean(identifierUnassigned));
        this.model.setExcludeSecuritiesInPieChart(preferences.getBoolean(identifierExclucdeSecuritiesInPieChart));
        this.model.setOrderByTaxonomyInStackChart(preferences.getBoolean(identifierOrderByTaxonomy));
        this.model.setExpansionStateDefinition(preferences.getString(expansionStateDefinition));
        this.model.setExpansionStateRebalancing(preferences.getString(expansionStateReblancing));
        this.model.setColoringStrategy(preferences.getString(identifierColoringStrategy));
        this.model.setShowGroupHeadingInTreeMap(preferences.getBoolean(identifierGroupHeading));
        this.model.setColorSchemaInTreeMap(preferences.getString(identifierColorSchema));

        this.taxonomy.addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event)
    {
        updateTitle(getDefaultTitle());
    }

    @Override
    public void dispose()
    {
        taxonomy.removePropertyChangeListener(this);

        Control[] children = container.getChildren();
        for (Control control : children)
        {
            Page page = (Page) control.getData();
            page.dispose();
        }

        // store preferences *after* disposing pages -> allow pages to update
        // the model
        IPreferenceStore preferences = getPreferenceStore();
        preferences.setValue(identifierUnassigned, model.isUnassignedCategoryInChartsExcluded());
        preferences.setValue(identifierExclucdeSecuritiesInPieChart, model.isSecuritiesInPieChartExcluded());
        preferences.setValue(identifierOrderByTaxonomy, model.isOrderByTaxonomyInStackChart());
        preferences.setValue(expansionStateDefinition, model.getExpansionStateDefinition());
        preferences.setValue(expansionStateReblancing, model.getExpansionStateRebalancing());
        preferences.setValue(identifierColoringStrategy, model.getColoringStrategy());
        preferences.setValue(identifierGroupHeading, model.doShowGroupHeadingInTreeMap());
        preferences.setValue(identifierColorSchema, model.getColorSchemaInTreeMap());

        super.dispose();
    }

    @Override
    protected void addButtons(final ToolBarManager toolBar)
    {
        addView(toolBar, Messages.LabelViewTaxonomyDefinition, Images.VIEW_TABLE, 0);
        addView(toolBar, Messages.LabelViewReBalancing, Images.VIEW_REBALANCING, 1);
        addView(toolBar, Messages.LabelViewPieChart, Images.VIEW_PIECHART, 2);
        addView(toolBar, Messages.LabelViewDonutChart, Images.VIEW_DONUT, 3);
        addView(toolBar, Messages.LabelViewTreeMap, Images.VIEW_TREEMAP, 4);
        addView(toolBar, Messages.LabelViewStackedChart, Images.VIEW_STACKEDCHART, 5);
        addView(toolBar, Messages.LabelViewStackedChartActualValue, Images.VIEW_STACKEDCHART_ACTUALVALUE, 6);
        toolBar.add(new Separator());

        addReportingPeriodDropDown(toolBar);
        addSearchButton(toolBar);

        toolBar.add(new Separator());

        toolBar.add(new FilterDropDown(getPreferenceStore()));
        toolBar.add(clientFilterDropDown);
        addExportButton(toolBar);
        addConfigButton(toolBar);
    }

    private void addReportingPeriodDropDown(ToolBarManager toolBar)
    {
        reportingPeriodDropDown = new ReportingPeriodDropDown(getPart(), this);
        toolBar.add(reportingPeriodDropDown);
    }

    private void addSearchButton(ToolBarManager toolBar)
    {
        toolBar.add(new ControlContribution("searchbox") //$NON-NLS-1$
        {
            @Override
            protected Control createControl(Composite parent)
            {
                final Text search = new Text(parent, SWT.SEARCH | SWT.ICON_CANCEL);
                search.setMessage(Messages.LabelSearch);
                search.setSize(300, SWT.DEFAULT);

                search.addModifyListener(e -> {
                    String filterText = Pattern.quote(search.getText().trim());
                    if (filterText.isEmpty())
                    {
                        model.setFilterPattern(null);
                        model.fireTaxonomyModelChange(model.getVirtualRootNode());
                    }
                    else
                    {
                        Pattern p = Pattern.compile(".*" + filterText + ".*", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$ //$NON-NLS-2$
                        model.setFilterPattern(p);
                        model.fireTaxonomyModelChange(model.getVirtualRootNode());
                    }
                });

                return search;
            }

            @Override
            protected int computeWidth(Control control)
            {
                return control.computeSize(100, SWT.DEFAULT, true).x;
            }
        });
    }

    private void addExportButton(ToolBarManager toolBar)
    {
        toolBar.add(new DropDown(Messages.MenuExportData, Images.EXPORT, SWT.NONE,
                        manager -> getCurrentPage().ifPresent(p -> p.exportMenuAboutToShow(manager))));
    }

    private void addConfigButton(ToolBarManager toolBar)
    {
        if (enableExperimentalFeatures)
        {
            toolBar.add(new DropDown("Sync", Images.CLOUD, SWT.NONE, manager -> { //$NON-NLS-1$

                String source = taxonomy.getSource();

                for (TaxonomySource ts : TaxonomySource.values())
                {
                    Action action = new SimpleAction(ts.getLabel(), a -> {
                        if (ts.getIdentifier().equals(source))
                            taxonomy.setSource(null);
                        else
                            taxonomy.setSource(ts.getIdentifier());

                        model.getClient().touch();
                    });
                    action.setChecked(ts.getIdentifier().equals(source));
                    manager.add(action);
                }
            }));
        }

        toolBar.add(new DropDown(Messages.MenuShowHideColumns, Images.CONFIG, SWT.NONE,
                        manager -> getCurrentPage().ifPresent(p -> p.configMenuAboutToShow(manager))));
    }

    private Optional<Page> getCurrentPage()
    {
        StackLayout layout = (StackLayout) container.getLayout();
        return layout.topControl != null ? Optional.of((Page) layout.topControl.getData()) : Optional.empty();
    }

    @Override
    public void notifyModelUpdated()
    {
        Client filteredClient = this.clientFilterDropDown.getSelectedFilter().filter(getClient());
        setToContext(UIConstants.Context.FILTERED_CLIENT, filteredClient);
        model.updateClientSnapshot(filteredClient);
    }

    private void addView(final ToolBarManager toolBar, String label, Images image, final int index)
    {
        Action showDefinition = new SimpleAction(label, IAction.AS_CHECK_BOX, a -> activateView(index));
        showDefinition.setImageDescriptor(image.descriptor());
        showDefinition.setToolTipText(label);
        toolBar.add(showDefinition);
        viewActions.add(showDefinition);
    }

    @Override
    protected Control createBody(Composite parent)
    {
        LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources(), parent);

        TaxonomyNodeRenderer renderer = new TaxonomyNodeRenderer(model, resources);

        container = new Composite(parent, SWT.NONE);
        StackLayout layout = new StackLayout();
        container.setLayout(layout);

        Page[] pages = new Page[] { make(DefinitionViewer.class, this, model, renderer), //
                        make(ReBalancingViewer.class, this, model, renderer), //
                        make(PieChartViewer.class, model, renderer), //
                        make(DonutViewer.class, model, renderer), //
                        make(TreeMapViewer.class, model, renderer), //
                        make(StackedChartViewer.class, model, renderer),
                        make(StackedChartActualValueViewer.class, model, renderer) };

        for (Page page : pages)
        {
            Control control = page.createControl(container);
            control.setData(page);
        }

        activateView(getPart().getPreferenceStore().getInt(identifierView));

        model.addDirtyListener(this::markDirty);

        return container;
    }

    private void activateView(final int index)
    {
        StackLayout layout = (StackLayout) container.getLayout();
        Control[] children = container.getChildren();

        if (index >= 0 && index < children.length)
        {
            if (layout.topControl != null)
                ((Page) layout.topControl.getData()).afterPage();

            Page page = (Page) children[index].getData();

            page.beforePage();

            layout.topControl = children[index];
            container.layout();

            for (int ii = 0; ii < viewActions.size(); ii++)
                viewActions.get(ii).setChecked(index == ii);

            reportingPeriodDropDown.setEnabled(page instanceof ReportingPeriodListener);

            getPart().getPreferenceStore().setValue(identifierView, index);
        }
    }

    @Override
    protected void addPanePages(List<InformationPanePage> pages)
    {
        super.addPanePages(pages);
        pages.add(make(SecurityPriceChartPane.class));
        pages.add(make(HistoricalPricesPane.class));
        pages.add(make(TransactionsPane.class));
        pages.add(make(TradesPane.class));
        pages.add(make(SecurityEventsPane.class));
    }

    @Override
    public void reportingPeriodUpdated()
    {
        for (Control control : container.getChildren())
        {
            Page page = (Page) control.getData();
            if (page instanceof ReportingPeriodListener listener)
            {
                listener.reportingPeriodUpdated();
            }
        }
    }
}
