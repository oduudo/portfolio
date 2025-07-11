package name.abuchen.portfolio.ui.views;

import static name.abuchen.portfolio.util.CollectorsUtil.toMutableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.eclipse.e4.core.di.extensions.Preference;
import org.eclipse.e4.ui.services.IStylingEngine;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Adaptable;
import name.abuchen.portfolio.model.Annotated;
import name.abuchen.portfolio.model.Attributable;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.InvestmentVehicle;
import name.abuchen.portfolio.model.Named;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.model.TransactionOwner;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.ExchangeRate;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.MoneyCollectors;
import name.abuchen.portfolio.money.Quote;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.AssetCategory;
import name.abuchen.portfolio.snapshot.AssetPosition;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.GroupByTaxonomy;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.snapshot.SecurityPosition;
import name.abuchen.portfolio.snapshot.filter.ClientFilter;
import name.abuchen.portfolio.snapshot.filter.ReadOnlyAccount;
import name.abuchen.portfolio.snapshot.filter.ReadOnlyPortfolio;
import name.abuchen.portfolio.snapshot.security.LazySecurityPerformanceRecord;
import name.abuchen.portfolio.snapshot.security.LazySecurityPerformanceRecord.LazyValue;
import name.abuchen.portfolio.snapshot.security.LazySecurityPerformanceSnapshot;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.dnd.ImportFromFileDropAdapter;
import name.abuchen.portfolio.ui.dnd.ImportFromURLDropAdapter;
import name.abuchen.portfolio.ui.dnd.SecurityDragListener;
import name.abuchen.portfolio.ui.dnd.SecurityTransfer;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.selection.SecuritySelection;
import name.abuchen.portfolio.ui.selection.SelectionService;
import name.abuchen.portfolio.ui.util.AttributeComparator;
import name.abuchen.portfolio.ui.util.CacheKey;
import name.abuchen.portfolio.ui.util.Colors;
import name.abuchen.portfolio.ui.util.LabelOnly;
import name.abuchen.portfolio.ui.util.SimpleAction;
import name.abuchen.portfolio.ui.util.viewers.Column;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport.MarkDirtyClientListener;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport.TouchClientListener;
import name.abuchen.portfolio.ui.util.viewers.ColumnViewerSorter;
import name.abuchen.portfolio.ui.util.viewers.CopyPasteSupport;
import name.abuchen.portfolio.ui.util.viewers.DateLabelProvider;
import name.abuchen.portfolio.ui.util.viewers.OptionLabelProvider;
import name.abuchen.portfolio.ui.util.viewers.ReportingPeriodColumnOptions;
import name.abuchen.portfolio.ui.util.viewers.SharesLabelProvider;
import name.abuchen.portfolio.ui.util.viewers.ShowHideColumnHelper;
import name.abuchen.portfolio.ui.util.viewers.StringEditingSupport;
import name.abuchen.portfolio.ui.views.columns.AttributeColumn;
import name.abuchen.portfolio.ui.views.columns.DistanceFromAllTimeHighColumn;
import name.abuchen.portfolio.ui.views.columns.DistanceFromMovingAverageColumn;
import name.abuchen.portfolio.ui.views.columns.DividendPaymentColumns;
import name.abuchen.portfolio.ui.views.columns.IsinColumn;
import name.abuchen.portfolio.ui.views.columns.NameColumn;
import name.abuchen.portfolio.ui.views.columns.NameColumn.NameColumnLabelProvider;
import name.abuchen.portfolio.ui.views.columns.NoteColumn;
import name.abuchen.portfolio.ui.views.columns.QuoteRangeColumn;
import name.abuchen.portfolio.ui.views.columns.SymbolColumn;
import name.abuchen.portfolio.ui.views.columns.TaxonomyColumn;
import name.abuchen.portfolio.ui.views.columns.WknColumn;
import name.abuchen.portfolio.util.Interval;
import name.abuchen.portfolio.util.TextUtil;

public class StatementOfAssetsViewer
{
    public static final class Model
    {
        private static final String TOP = Model.class.getSimpleName() + "@top"; //$NON-NLS-1$
        private static final String BOTTOM = Model.class.getSimpleName() + "@bottom"; //$NON-NLS-1$

        private final IPreferenceStore preferences;

        private final ClientFilter clientFilter;
        private final Client filteredClient;
        private CurrencyConverter converter;

        private ClientSnapshot clientSnapshot;
        private List<Element> elements = new ArrayList<>();
        private GroupByTaxonomy groupByTaxonomy;

        private final Interval globalInterval;
        private Set<CacheKey> calculated = new HashSet<>();

        private boolean hideTotalsAtTheTop;
        private boolean hideTotalsAtTheBottom;

        public Model(IPreferenceStore preferences, Client client, ClientFilter filter, CurrencyConverter converter,
                        LocalDate date, Taxonomy taxonomy)
        {
            this.preferences = preferences;

            this.clientFilter = filter;
            this.filteredClient = filter.filter(client);
            this.converter = converter;

            this.globalInterval = Interval.of(LocalDate.MIN, date);

            this.clientSnapshot = ClientSnapshot.create(filteredClient, converter, date);

            this.groupByTaxonomy = clientSnapshot.groupByTaxonomy(taxonomy);

            this.hideTotalsAtTheTop = preferences.getBoolean(TOP);
            this.hideTotalsAtTheBottom = preferences.getBoolean(BOTTOM);

            this.elements.addAll(flatten(groupByTaxonomy));
        }

        public List<Element> getElements()
        {
            return elements.stream().filter(e -> e.getSortOrder() != 0 || !hideTotalsAtTheTop)
                            .filter(e -> e.getSortOrder() != Integer.MAX_VALUE || !hideTotalsAtTheBottom)
                            .collect(Collectors.toList());
        }

        public LocalDate getDate()
        {
            return clientSnapshot.getTime();
        }

        public CurrencyConverter getCurrencyConverter()
        {
            return converter;
        }

        public Interval getGlobalInterval()
        {
            return globalInterval;
        }

        public boolean isHideTotalsAtTheTop()
        {
            return hideTotalsAtTheTop;
        }

        public void setHideTotalsAtTheTop(boolean hideTotalsAtTheTop)
        {
            this.hideTotalsAtTheTop = hideTotalsAtTheTop;
            preferences.setValue(TOP, hideTotalsAtTheTop);
        }

        public boolean isHideTotalsAtTheBottom()
        {
            return hideTotalsAtTheBottom;
        }

        public void setHideTotalsAtTheBottom(boolean hideTotalsAtTheBottom)
        {
            this.hideTotalsAtTheBottom = hideTotalsAtTheBottom;
            preferences.setValue(BOTTOM, hideTotalsAtTheBottom);
        }

        private final List<Element> flatten(GroupByTaxonomy groupByTaxonomy)
        {
            // when flattening, assign sortOrder to keep the tree structure for
            // sorting (only positions within a category are sorted)
            int sortOrder = 1;
            List<Element> answer = new ArrayList<>();

            // totals elements
            Element totalTop = new Element(groupByTaxonomy, 0);
            Element totalBottom = new Element(groupByTaxonomy, Integer.MAX_VALUE);

            answer.add(totalTop);

            for (AssetCategory cat : groupByTaxonomy.asList())
            {
                Element category = new Element(groupByTaxonomy, cat, sortOrder);
                answer.add(category);
                totalTop.addChild(category);
                totalBottom.addChild(category);
                sortOrder++;

                for (AssetPosition p : cat.getPositions())
                {
                    Element child = new Element(groupByTaxonomy, p, sortOrder);
                    answer.add(child);
                    category.addChild(child);
                }
                sortOrder++;
            }

            answer.add(totalBottom);

            return answer;
        }

        /* package */ final void calculatePerformanceAndInjectIntoElements(String currencyCode, Interval interval)
        {
            CacheKey key = new CacheKey(currencyCode, interval);

            // already calculated?
            if (calculated.contains(key))
                return;

            // performance for securities
            var snapshot = LazySecurityPerformanceSnapshot.create(filteredClient, converter.with(currencyCode),
                            interval);

            Map<Security, LazySecurityPerformanceRecord> map = snapshot.getRecords().stream()
                            .collect(Collectors.toMap(LazySecurityPerformanceRecord::getSecurity, r -> r));

            elements.stream().filter(Element::isSecurity)
                            .forEach(e -> e.setPerformance(currencyCode, interval, map.get(e.getSecurity())));

            // create (lazily!) the performance index for categories

            elements.stream() //
                            .filter(Element::isCategory) //
                            .filter(e -> !Objects.equals(Classification.UNASSIGNED_ID,
                                            e.getCategory().getClassification().getId()))
                            .forEach(e -> {
                                var index = new LazyValue<PerformanceIndex>(() -> PerformanceIndex.forClassification(
                                                filteredClient, converter.with(currencyCode),
                                                e.getCategory().getClassification(), interval, new ArrayList<>()));
                                e.setPerformanceForCategoryTotals(currencyCode, interval, index);
                            });

            // create (lazily!) the performance index for the total lines
            var index = new LazyValue<PerformanceIndex>(() -> PerformanceIndex.forClient(filteredClient,
                            converter.with(currencyCode), interval, new ArrayList<>()));
            elements.stream().filter(Element::isGroupByTaxonomy)
                            .forEach(e -> e.setPerformanceForCategoryTotals(currencyCode, interval, index));

            calculated.add(key);
        }
    }

    @Inject
    private IPreferenceStore preference;

    @Inject
    private SelectionService selectionService;

    @Inject
    private IStylingEngine stylingEngine;

    private boolean useIndirectQuotation = false;

    private TableViewer assets;

    private Font boldFont;
    private Menu contextMenu;

    private AbstractFinanceView owner;
    private ShowHideColumnHelper support;

    private final Client client;
    private Taxonomy taxonomy;
    private Model model;

    @Inject
    public StatementOfAssetsViewer(AbstractFinanceView owner, Client client)
    {
        this.owner = owner;
        this.client = client;
    }

    @Inject
    public void setUseIndirectQuotation(
                    @Preference(value = UIConstants.Preferences.USE_INDIRECT_QUOTATION) boolean useIndirectQuotation)
    {
        this.useIndirectQuotation = useIndirectQuotation;

        if (assets != null)
            assets.refresh();
    }

    public Control createControl(Composite parent, boolean isConfigurable)
    {
        Control control = createColumns(parent, isConfigurable);

        this.assets.getTable().addDisposeListener(e -> StatementOfAssetsViewer.this.widgetDisposed());

        return control;
    }

    @PostConstruct
    private void loadTaxonomy() // NOSONAR
    {
        String taxonomyId = preference.getString(this.getClass().getSimpleName());

        if (taxonomyId != null)
        {
            for (Taxonomy t : client.getTaxonomies())
            {
                if (taxonomyId.equals(t.getId()))
                {
                    this.taxonomy = t;
                    break;
                }
            }
        }

        if (this.taxonomy == null && !client.getTaxonomies().isEmpty())
            this.taxonomy = client.getTaxonomies().get(0);
    }

    private Control createColumns(Composite parent, boolean isConfigurable) // NOSONAR
    {
        Composite container = new Composite(parent, SWT.NONE);
        TableColumnLayout layout = new TableColumnLayout();
        container.setLayout(layout);

        assets = new TableViewer(container, SWT.FULL_SELECTION | SWT.MULTI);
        ColumnViewerToolTipSupport.enableFor(assets, ToolTip.NO_RECREATE);
        ColumnEditingSupport.prepare(owner.getEditorActivationState(), assets);
        CopyPasteSupport.enableFor(assets);

        ImportFromURLDropAdapter.attach(this.assets.getControl(), owner.getPart());
        ImportFromFileDropAdapter.attach(this.assets.getControl(), owner.getPart());

        assets.addSelectionChangedListener(event -> {
            List<Security> securities = event.getStructuredSelection().stream().filter(e -> ((Element) e).isSecurity())
                            .map(e -> ((Element) e).getSecurity()).toList();
            if (!securities.isEmpty())
                selectionService.setSelection(new SecuritySelection(client, securities));
            else
                selectionService.setSelection(null);
        });

        support = new ShowHideColumnHelper(StatementOfAssetsViewer.class.getName(), client, preference, assets, layout);

        Column column = new Column("0", Messages.ColumnSharesOwned, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setLabelProvider(new SharesLabelProvider() // NOSONAR
        {
            @Override
            public Long getValue(Object e)
            {
                Element element = (Element) e;
                return element.isSecurity() ? element.getSecurityPosition().getShares() : null;
            }
        });
        column.setComparator(new ElementComparator(new AttributeComparator(
                        e -> ((Element) e).isSecurity() ? ((Element) e).getSecurityPosition().getShares() : null)));
        support.addColumn(column);

        column = new NameColumn(client, "1"); //$NON-NLS-1$
        column.setLabelProvider(new NameColumnLabelProvider(client) // NOSONAR
        {
            @Override
            public String getText(Object e)
            {
                Element el = (Element) e;
                if (((Element) e).isGroupByTaxonomy())
                    return Messages.ColumnSum;
                if (el.isCategory())
                    return super.getText(el) + " (" + el.getChildren().count() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                return super.getText(e);
            }

            @Override
            public Font getFont(Object e)
            {
                return ((Element) e).isGroupByTaxonomy() || ((Element) e).isCategory() ? boldFont : null;
            }

            @Override
            public Image getImage(Object e)
            {
                if (((Element) e).isCategory())
                    return null;
                return super.getImage(e);
            }
        });
        column.setEditingSupport(new StringEditingSupport(Named.class, "name") //$NON-NLS-1$
        {
            @Override
            public boolean canEdit(Object element)
            {
                boolean isCategory = ((Element) element).isCategory();
                boolean isUnassignedCategory = isCategory && Classification.UNASSIGNED_ID
                                .equals(((Element) element).getCategory().getClassification().getId());

                return !isUnassignedCategory ? super.canEdit(element) : false;
            }

        }.setMandatory(true).addListener(new TouchClientListener(client)));
        column.getSorter().wrap(ElementComparator::new);
        support.addColumn(column);

        column = new IsinColumn("3"); //$NON-NLS-1$
        column.getEditingSupport().addListener(new TouchClientListener(client));
        column.getSorter().wrap(ElementComparator::new);
        column.setVisible(false);
        support.addColumn(column);

        column = new SymbolColumn("2"); //$NON-NLS-1$
        column.getEditingSupport().addListener(new TouchClientListener(client));
        column.getSorter().wrap(ElementComparator::new);
        support.addColumn(column);

        column = new WknColumn("12"); //$NON-NLS-1$
        column.getEditingSupport().addListener(new TouchClientListener(client));
        column.getSorter().wrap(ElementComparator::new);
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("4", Messages.ColumnQuote, SWT.RIGHT, 60); //$NON-NLS-1$
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Element element = (Element) e;
                if (!element.isSecurity())
                    return null;

                Security security = element.getSecurity();
                return Values.Quote.format(security.getCurrencyCode(),
                                element.getSecurityPosition().getPrice().getValue(), client.getBaseCurrency());
            }
        });
        column.setComparator(new ElementComparator(new AttributeComparator(e -> {
            Element element = (Element) e;
            if (!element.isSecurity())
                return null;

            return Money.of(element.getSecurity().getCurrencyCode(),
                            element.getSecurityPosition().getPrice().getValue());
        })));
        support.addColumn(column);

        column = new Column("qdate", Messages.ColumnDateOfQuote, SWT.LEFT, 80); //$NON-NLS-1$
        column.setLabelProvider(new DateLabelProvider(e -> {
            Element element = (Element) e;
            return element.isSecurity() ? element.getSecurityPosition().getPrice().getDate() : null;
        }));
        column.setComparator(new ElementComparator(new AttributeComparator(
                        e -> ((Element) e).isSecurity() ? ((Element) e).getSecurityPosition().getPrice().getDate()
                                        : null)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("5", Messages.ColumnMarketValue, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Element element = (Element) e;
                return Values.Money.format(element.getValuation(), client.getBaseCurrency());
            }

            @Override
            public Font getFont(Object e)
            {
                return ((Element) e).isGroupByTaxonomy() || ((Element) e).isCategory() ? boldFont : null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(Element.class, "valuation").wrap(ElementComparator::new)); //$NON-NLS-1$
        support.addColumn(column);

        column = new Column("6", Messages.ColumnShareInPercent, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Element element = (Element) e;
                if (element.isGroupByTaxonomy())
                    return Values.Percent.format(1d);
                if (element.isCategory())
                    return Values.Percent.format(element.getCategory().getShare());
                else
                    return Values.Percent.format(element.getPosition().getShare());
            }

            @Override
            public Font getFont(Object e)
            {
                return ((Element) e).isGroupByTaxonomy() || ((Element) e).isCategory() ? boldFont : null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(Element.class, "valuation").wrap(ElementComparator::new)); //$NON-NLS-1$
        support.addColumn(column);

        addPurchaseCostColumns();

        ReportingPeriodLabelProvider labelProvider;

        // cost value - FIFO
        column = new Column("8", Messages.ColumnPurchaseValue, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.ColumnPurchaseValue);
        column.setHeading(Messages.LabelTaxesAndFeesIncluded);
        column.setMenuLabel(Messages.ColumnPurchaseValue_MenuLabel);
        column.setDescription(Messages.ColumnPurchaseValue_Description);
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getFifoCost, withSum()), false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        // cost value - moving average
        column = new Column("pvmvavg", Messages.ColumnPurchaseValueMovingAverage, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.ColumnPurchaseValue);
        column.setMenuLabel(Messages.ColumnPurchaseValueMovingAverage_MenuLabel);
        column.setDescription(Messages.ColumnPurchaseValueMovingAverage_Description);
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getMovingAverageCost, withSum()),
                        false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("9", Messages.ColumnProfitLoss, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getCapitalGainsOnHoldings, withSum()),
                        true);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new NoteColumn();
        column.getEditingSupport().addListener(new TouchClientListener(client));
        column.getSorter().wrap(ElementComparator::new);
        support.addColumn(column);

        // create a modifiable copy as all menus share the same list of
        // reporting periods
        List<ReportingPeriod> options = owner.getPart().getReportingPeriods().stream().collect(toMutableList());

        addPerformanceColumns(options);
        addDividendColumns(options);
        addTaxonomyColumns();
        addAttributeColumns();
        addCurrencyColumns();

        column = new DistanceFromMovingAverageColumn(() -> model.getDate());
        column.getSorter().wrap(ElementComparator::new);
        support.addColumn(column);

        column = new DistanceFromAllTimeHighColumn(() -> model.getDate(), options);
        column.getSorter().wrap(ElementComparator::new);
        support.addColumn(column);

        column = new QuoteRangeColumn(LocalDate::now,
                        owner.getPart().getReportingPeriods().stream().collect(toMutableList()));
        column.getSorter().wrap(ElementComparator::new);
        support.addColumn(column);

        support.createColumns(isConfigurable);

        assets.getTable().setHeaderVisible(true);
        assets.getTable().setLinesVisible(true);

        assets.setContentProvider(ArrayContentProvider.getInstance());

        assets.addDragSupport(DND.DROP_MOVE, //
                        new Transfer[] { SecurityTransfer.getTransfer() }, //
                        new SecurityDragListener(assets));

        // make sure to apply the styles (including font information to the
        // table) before creating the bold font. Otherwise the font does not
        // match the styles in CSS
        stylingEngine.style(assets.getTable());

        LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources(), assets.getTable());
        boldFont = resources.create(FontDescriptor.createFrom(assets.getTable().getFont()).setStyle(SWT.BOLD));

        return container;
    }

    private void addPurchaseCostColumns()
    {
        ReportingPeriodLabelProvider labelProvider;

        // cost value per share - FIFO
        Column column = new Column("7", Messages.ColumnPurchasePrice, SWT.RIGHT, 60); //$NON-NLS-1$
        column.setHeading(Messages.LabelTaxesAndFeesNotIncluded);
        column.setGroupLabel(Messages.LabelPurchasePrice);
        column.setMenuLabel(Messages.ColumnPurchasePrice_MenuLabel);
        column.setDescription(Messages.ColumnPurchasePrice_Description);
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getFifoCostPerSharesHeld, null), false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        // cost value per share - moving average
        column = new Column("ppmvavg", Messages.ColumnPurchasePriceMovingAverage, SWT.RIGHT, 60); //$NON-NLS-1$
        column.setGroupLabel(Messages.LabelPurchasePrice);
        column.setMenuLabel(Messages.ColumnPurchasePriceMovingAverage_MenuLabel);
        column.setDescription(Messages.ColumnPurchasePriceMovingAverage_Description);
        labelProvider = new ReportingPeriodLabelProvider(new ElementValueProvider(
                        LazySecurityPerformanceRecord::getMovingAverageCostPerSharesHeld, null), false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        // cost value per share including fees and taxes - FIFO
        column = new Column("grossPurchasePriceFIFO", Messages.ColumnGrossPurchasePriceFIFO, SWT.RIGHT, 60); //$NON-NLS-1$
        column.setHeading(Messages.LabelTaxesAndFeesIncluded);
        column.setGroupLabel(Messages.LabelPurchasePrice);
        column.setMenuLabel(Messages.ColumnPurchasePrice_MenuLabel);
        column.setDescription(Messages.ColumnGrossPurchasePriceFIFO_Description);
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getGrossFifoCostPerSharesHeld, null),
                        false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        // cost value per share including fees and taxes - moving average
        column = new Column("grossPurchasePriceMA", Messages.ColumnGrossPurchasePriceMovingAverage, SWT.RIGHT, 60); //$NON-NLS-1$
        column.setGroupLabel(Messages.LabelPurchasePrice);
        column.setMenuLabel(Messages.ColumnPurchasePriceMovingAverage_MenuLabel);
        column.setDescription(Messages.ColumnGrossPurchasePriceMovingAverage_Description);
        labelProvider = new ReportingPeriodLabelProvider(new ElementValueProvider(
                        LazySecurityPerformanceRecord::getGrossMovingAverageCostPerSharesHeld, null), false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);
    }

    private void addPerformanceColumns(List<ReportingPeriod> options)
    {
        ReportingPeriodLabelProvider labelProvider;

        Column column = new Column("ttwror", Messages.ColumnTTWROR, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(LazySecurityPerformanceRecord::getTrueTimeWeightedRateOfReturn,
                        true, PerformanceIndex::getFinalAccumulatedPercentage);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnTTWROR_Option, options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.LabelTTWROR);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("ttwror_pa", Messages.ColumnTTWRORpa, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(
                        LazySecurityPerformanceRecord::getTrueTimeWeightedRateOfReturnAnnualized, true,
                        PerformanceIndex::getFinalAccumulatedAnnualizedPercentage);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnTTWRORpa_Option, options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.LabelTTWROR_Annualized);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("irr", Messages.ColumnIRR, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(LazySecurityPerformanceRecord::getIrr, true,
                        PerformanceIndex::getPerformanceIRR);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnIRRPerformanceOption, options));
        column.setMenuLabel(Messages.ColumnIRR_MenuLabel);
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("capitalgains", Messages.ColumnCapitalGains, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(LazySecurityPerformanceRecord::getCapitalGainsOnHoldings,
                        withSum(), true);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnCapitalGains_Option, options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.ColumnCapitalGains_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("capitalgains%", Messages.ColumnCapitalGainsPercent, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(
                        LazySecurityPerformanceRecord::getCapitalGainsOnHoldingsPercent);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnCapitalGainsPercent_Option, options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.ColumnCapitalGainsPercent_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("capitalgainsmvavg", Messages.ColumnCapitalGainsMovingAverage, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(
                        LazySecurityPerformanceRecord::getCapitalGainsOnHoldingsMovingAverage, withSum(), true);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnCapitalGainsMovingAverage_Option, options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.ColumnCapitalGainsMovingAverage_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("capitalgainsmvavg%", Messages.ColumnCapitalGainsMovingAveragePercent, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(
                        LazySecurityPerformanceRecord::getCapitalGainsOnHoldingsMovingAveragePercent);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnCapitalGainsMovingAveragePercent_Option,
                        options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.ColumnCapitalGainsMovingAveragePercent_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("delta", Messages.ColumnAbsolutePerformance_MenuLabel, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(LazySecurityPerformanceRecord::getDelta, withSum(), true);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnAbsolutePerformance_Option, options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.ColumnAbsolutePerformance_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("delta%", Messages.ColumnAbsolutePerformancePercent_MenuLabel, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(LazySecurityPerformanceRecord::getDeltaPercent);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnAbsolutePerformancePercent_Option, options));
        column.setGroupLabel(Messages.GroupLabelPerformance);
        column.setDescription(Messages.ColumnAbsolutePerformancePercent_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);
    }

    private void addDividendColumns(List<ReportingPeriod> options)
    {
        ReportingPeriodLabelProvider labelProvider;

        Column column = new Column("sumdiv", Messages.ColumnDividendSum, SWT.RIGHT, 80); //$NON-NLS-1$

        labelProvider = new ReportingPeriodLabelProvider(LazySecurityPerformanceRecord::getSumOfDividends, withSum(),
                        false);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnDividendSum + " {0}", options)); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelDividends);
        column.setMenuLabel(Messages.ColumnDividendSum_MenuLabel);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("d%", Messages.ColumnDividendTotalRateOfReturn, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(LazySecurityPerformanceRecord::getTotalRateOfReturnDiv, null,
                        false);
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnDividendTotalRateOfReturn + " {0}", options)); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelDividends);
        column.setDescription(Messages.ColumnDividendTotalRateOfReturn_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("d%mvavg", Messages.ColumnDividendMovingAverageTotalRateOfReturn, SWT.RIGHT, 80); //$NON-NLS-1$
        labelProvider = new ReportingPeriodLabelProvider(
                        LazySecurityPerformanceRecord::getTotalRateOfReturnDivMovingAverage, null, false);
        column.setOptions(new ReportingPeriodColumnOptions(
                        Messages.ColumnDividendMovingAverageTotalRateOfReturn + " {0}", options)); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelDividends);
        column.setDescription(Messages.ColumnDividendMovingAverageTotalRateOfReturn_Description);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        addDividendPaymentColumns();
    }

    private void addDividendPaymentColumns()
    {
        DividendPaymentColumns.createFor(client) //
                        .forEach(column -> {
                            if (column.getSorter() != null)
                                column.getSorter().wrap(ElementComparator::new);
                            support.addColumn(column);
                        });
    }

    private void addAttributeColumns()
    {
        AttributeColumn.createFor(client, Security.class) //
                        .forEach(column -> {
                            if (column.getSorter() != null)
                                column.getSorter().wrap(ElementComparator::new);
                            column.getEditingSupport().addListener(new MarkDirtyClientListener(client));
                            support.addColumn(column);
                        });
    }

    private void addTaxonomyColumns()
    {
        for (Taxonomy t : client.getTaxonomies())
        {
            Column column = new TaxonomyColumn(t);
            column.setVisible(false);
            if (column.getSorter() != null)
                column.getSorter().wrap(ElementComparator::new);
            support.addColumn(column);
        }
    }

    private void addCurrencyColumns() // NOSONAR
    {
        Column column = new Column("baseCurrency", Messages.ColumnCurrency, SWT.LEFT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.ColumnForeignCurrencies);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Element element = (Element) e;
                if (!element.isPosition())
                    return null;

                return element.getPosition().getInvestmentVehicle().getCurrencyCode();
            }
        });
        column.setComparator(new ElementComparator(new AttributeComparator(e -> ((Element) e).isPosition()
                        ? ((Element) e).getPosition().getInvestmentVehicle().getCurrencyCode()
                        : null)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("exchangeRate", Messages.ColumnExchangeRate, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.ColumnForeignCurrencies);
        column.setLabelProvider(new ColumnLabelProvider() // NOSONAR
        {
            @Override
            public String getText(Object e)
            {
                Element element = (Element) e;
                if (!element.isPosition())
                    return null;

                String baseCurrency = element.getPosition().getInvestmentVehicle().getCurrencyCode();
                CurrencyConverter converter = model.getCurrencyConverter();
                ExchangeRate rate = converter.getRate(model.getDate(), baseCurrency);

                if (useIndirectQuotation)
                    rate = rate.inverse();

                return Values.ExchangeRate.format(rate.getValue());
            }

            @Override
            public String getToolTipText(Object e)
            {
                String text = getText(e);
                if (text == null)
                    return null;

                String term = model.getCurrencyConverter().getTermCurrency();
                String base = ((Element) e).getPosition().getInvestmentVehicle().getCurrencyCode();

                return text + ' ' + (useIndirectQuotation ? base + '/' + term : term + '/' + base);
            }
        });
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("quoteReportingCurrency", Messages.ColumnQuote + Messages.BaseCurrencyCue, SWT.RIGHT, 60); //$NON-NLS-1$
        column.setGroupLabel(Messages.ColumnForeignCurrencies);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Element element = (Element) e;
                if (!element.isSecurity())
                    return null;

                Security security = element.getSecurity();
                var price = element.getSecurityPosition().getPrice();
                var converter = model.getCurrencyConverter();

                if (converter.getTermCurrency().equals(security.getCurrencyCode()))
                {
                    return Values.Quote.format(security.getCurrencyCode(), price.getValue(), client.getBaseCurrency());
                }
                else
                {
                    var converted = converter.convert(price.getDate(),
                                    Quote.of(security.getCurrencyCode(), price.getValue()));
                    return Values.CalculatedQuote.format(converted.getCurrencyCode(), converted.getAmount(),
                                    client.getBaseCurrency());
                }
            }
        });
        column.setComparator(new ElementComparator(new AttributeComparator(e -> {
            Element element = (Element) e;
            if (!element.isSecurity())
                return null;

            Security security = element.getSecurity();
            var price = element.getSecurityPosition().getPrice();
            var converter = model.getCurrencyConverter();

            if (converter.getTermCurrency().equals(security.getCurrencyCode()))
            {
                return Money.of(element.getSecurity().getCurrencyCode(), price.getValue());
            }
            else
            {
                var converted = converter.convert(price.getDate(),
                                Quote.of(security.getCurrencyCode(), price.getValue()));
                return Money.of(converted.getCurrencyCode(), converted.getAmount());
            }
        })));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("marketValueBaseCurrency", //$NON-NLS-1$
                        Messages.ColumnMarketValue + Messages.BaseCurrencyCue, SWT.RIGHT, 80);
        column.setDescription(Messages.ColumnMarketValueBaseCurrency);
        column.setGroupLabel(Messages.ColumnForeignCurrencies);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Element element = (Element) e;
                if (!element.isPosition())
                    return null;

                return Values.Money.format(element.getPosition().getPosition().calculateValue(),
                                client.getBaseCurrency());
            }
        });
        column.setComparator(new ElementComparator(new AttributeComparator(
                        e -> ((Element) e).isPosition() ? ((Element) e).getPosition().getPosition().calculateValue()
                                        : null)));
        column.setVisible(false);
        support.addColumn(column);

        ReportingPeriodLabelProvider labelProvider;

        column = new Column("purchaseValueBaseCurrency", //$NON-NLS-1$
                        Messages.ColumnPurchaseValue + Messages.BaseCurrencyCue, SWT.RIGHT, 80);
        column.setDescription(Messages.ColumnPurchaseValueBaseCurrency);
        column.setGroupLabel(Messages.ColumnForeignCurrencies);
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getFifoCost, null),
                        e -> e.isSecurity() ? e.getSecurity().getCurrencyCode()
                                        : model.getCurrencyConverter().getTermCurrency(),
                        false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("purchasePriceBaseCurrency", //$NON-NLS-1$
                        Messages.ColumnPurchasePrice + Messages.BaseCurrencyCue, SWT.RIGHT, 80);
        column.setDescription(Messages.ColumnPurchasePriceBaseCurrency);
        column.setGroupLabel(Messages.ColumnForeignCurrencies);
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getFifoCostPerSharesHeld, null),
                        e -> e.isSecurity() ? e.getSecurity().getCurrencyCode()
                                        : model.getCurrencyConverter().getTermCurrency(),
                        false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("profitLossBaseCurrency", //$NON-NLS-1$
                        Messages.ColumnProfitLoss + Messages.BaseCurrencyCue, SWT.RIGHT, 80);
        column.setDescription(Messages.ColumnProfitLossBaseCurrency);
        column.setGroupLabel(Messages.ColumnForeignCurrencies);
        labelProvider = new ReportingPeriodLabelProvider(
                        new ElementValueProvider(LazySecurityPerformanceRecord::getCapitalGainsOnHoldings, null),
                        e -> e.isSecurity() ? e.getSecurity().getCurrencyCode()
                                        : model.getCurrencyConverter().getTermCurrency(),
                        false);
        column.setLabelProvider(labelProvider);
        column.setSorter(ColumnViewerSorter.create(new ElementComparator(labelProvider)));
        column.setVisible(false);
        column.setVisible(false);
        support.addColumn(column);
    }

    public void setToolBarManager(ToolBarManager toolBar)
    {
        if (support == null)
            throw new NullPointerException("support"); //$NON-NLS-1$

        support.setToolBarManager(toolBar);
    }

    public void hookMenuListener(IMenuManager manager, final AbstractFinanceView view)
    {
        Element element = (Element) ((IStructuredSelection) assets.getSelection()).getFirstElement();
        if (element == null)
            return;

        if (element.isAccount())
        {
            new AccountContextMenu(view).menuAboutToShow(manager, ReadOnlyAccount.unwrap(element.getAccount()), null);
        }
        else if (element.isSecurity())
        {
            Portfolio reference = null;
            if (model.filteredClient.getPortfolios().size() == 1)
                reference = ReadOnlyPortfolio.unwrap(model.filteredClient.getPortfolios().get(0));

            new SecurityContextMenu(view).menuAboutToShow(manager, element.getSecurity(), reference);
        }
    }

    public TableViewer getTableViewer()
    {
        return assets;
    }

    public void showConfigMenu(Shell shell)
    {
        if (contextMenu == null)
        {
            MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
            menuMgr.setRemoveAllWhenShown(true);
            menuMgr.addMenuListener(StatementOfAssetsViewer.this::menuAboutToShow);

            contextMenu = menuMgr.createContextMenu(shell);
        }

        contextMenu.setVisible(true);
    }

    public void menuAboutToShow(IMenuManager manager)
    {
        manager.add(new LabelOnly(Messages.LabelTaxonomies));
        for (final Taxonomy t : client.getTaxonomies())
        {
            Action action = new SimpleAction(TextUtil.tooltip(t.getName()), a -> {
                taxonomy = t;
                setInput(model.clientFilter, model.getDate(), model.getCurrencyConverter());
            });
            action.setChecked(t.equals(taxonomy));
            manager.add(action);
        }

        manager.add(new Separator());
        manager.add(new LabelOnly(Messages.LabelColumns));
        support.menuAboutToShow(manager);

        manager.add(new Separator());

        MenuManager submenu = new MenuManager(Messages.PrefTitlePresentation);
        manager.add(submenu);

        Action action = new SimpleAction(Messages.LabelTotalsAtTheTop, a -> {
            model.setHideTotalsAtTheTop(!model.isHideTotalsAtTheTop());
            assets.setInput(model.getElements());
            assets.refresh();
        });
        action.setChecked(!model.isHideTotalsAtTheTop());
        submenu.add(action);

        action = new SimpleAction(Messages.LabelTotalsAtTheBottom, a -> {
            model.setHideTotalsAtTheBottom(!model.isHideTotalsAtTheBottom());
            assets.setInput(model.getElements());
            assets.refresh();
        });
        action.setChecked(!model.isHideTotalsAtTheBottom());
        submenu.add(action);

    }

    public void setInput(ClientFilter filter, LocalDate date, CurrencyConverter converter)
    {
        assets.getTable().setRedraw(false);
        try
        {
            this.model = new Model(preference, client, filter, converter, date, taxonomy);

            assets.setInput(model.getElements());
            assets.refresh();
        }
        finally
        {
            assets.getTable().setRedraw(true);
        }
    }

    public void selectSubject(Object subject)
    {
        model.getElements().stream().filter(e -> Objects.equals(e.getSubject(), subject)).findAny()
                        .ifPresent(e -> assets.setSelection(new StructuredSelection(e)));
    }

    public Function<Stream<Object>, Object> withSum()
    {
        return elements -> elements.map(e -> (Money) e)
                        .collect(MoneyCollectors.sum(model.getCurrencyConverter().getTermCurrency()));
    }

    public ShowHideColumnHelper getColumnHelper()
    {
        return support;
    }

    private void widgetDisposed()
    {
        if (taxonomy != null)
            preference.setValue(this.getClass().getSimpleName(), taxonomy.getId());

        if (contextMenu != null)
            contextMenu.dispose();
    }

    public static class Element implements Adaptable
    {
        /**
         * The sortOrder is used to separate asset categories and asset
         * positions and thereby sort positions only within a category even
         * though there is a flat list of elements. See
         * {@link ElementComparator}.
         */
        private final int sortOrder;

        private GroupByTaxonomy groupByTaxonomy;
        private AssetCategory category;
        private AssetPosition position;

        private List<Element> children = new ArrayList<>();

        private Map<CacheKey, LazySecurityPerformanceRecord> performance = new HashMap<>();
        private Map<CacheKey, LazyValue<PerformanceIndex>> performanceForCategoryTotals = new HashMap<>();

        private Element(GroupByTaxonomy groupByTaxonomy, AssetCategory category, int sortOrder)
        {
            this.groupByTaxonomy = groupByTaxonomy;
            this.category = category;
            this.sortOrder = sortOrder;
        }

        private Element(GroupByTaxonomy groupByTaxonomy, AssetPosition position, int sortOrder)
        {
            this.groupByTaxonomy = groupByTaxonomy;
            this.position = position;
            this.sortOrder = sortOrder;
        }

        private Element(GroupByTaxonomy groupByTaxonomy, int sortOrder)
        {
            this.groupByTaxonomy = groupByTaxonomy;
            this.sortOrder = sortOrder;
        }

        /**
         * Returns the primary object which identifies this element: the
         * investment vehicle, the classification or the grouping.
         */
        public Object getSubject()
        {
            if (position != null)
                return position.getInvestmentVehicle();
            else if (category != null)
                return category.getClassification();
            else
                return groupByTaxonomy;
        }

        public GroupByTaxonomy getGroupByTaxonomy()
        {
            return groupByTaxonomy;
        }

        public void addChild(Element child)
        {
            children.add(child);
        }

        public Stream<Element> getChildren()
        {
            return children.stream();
        }

        public int getSortOrder()
        {
            return sortOrder;
        }

        public void setPerformance(String currencyCode, Interval period, LazySecurityPerformanceRecord record)
        {
            performance.put(new CacheKey(currencyCode, period), record);
        }

        public LazySecurityPerformanceRecord getPerformance(String currencyCode, Interval period)
        {
            return performance.get(new CacheKey(currencyCode, period));
        }

        public void setPerformanceForCategoryTotals(String currencyCode, Interval period,
                        LazyValue<PerformanceIndex> index)
        {
            performanceForCategoryTotals.put(new CacheKey(currencyCode, period), index);
        }

        public PerformanceIndex getPerformanceForCategoryTotals(String currencyCode, Interval period)
        {
            return performanceForCategoryTotals.get(new CacheKey(currencyCode, period)).get();
        }

        public boolean isGroupByTaxonomy()
        {
            return groupByTaxonomy != null && category == null && position == null;
        }

        public boolean isCategory()
        {
            return category != null;
        }

        public boolean isPosition()
        {
            return position != null;
        }

        public boolean isSecurity()
        {
            return position != null && position.getSecurity() != null;
        }

        public boolean isAccount()
        {
            return position != null && position.getInvestmentVehicle() instanceof Account;
        }

        public AssetCategory getCategory()
        {
            return category;
        }

        public AssetPosition getPosition()
        {
            return position;
        }

        public SecurityPosition getSecurityPosition()
        {
            return position != null ? position.getPosition() : null;
        }

        public Security getSecurity()
        {
            return position != null ? position.getSecurity() : null;
        }

        public Account getAccount()
        {
            return isAccount() ? (Account) position.getInvestmentVehicle() : null;
        }

        public Money getValuation()
        {
            if (position != null)
                return position.getValuation();
            else if (category != null)
                return category.getValuation();
            else
                return groupByTaxonomy.getValuation();
        }

        @Override
        public <T> T adapt(Class<T> type) // NOSONAR
        {
            if (type == Security.class || type == Attributable.class)
            {
                return type.cast(getSecurity());
            }
            else if (type == Named.class || type == Annotated.class)
            {
                if (isSecurity())
                    return type.cast(getSecurity());
                else if (isAccount())
                    return type.cast(getAccount());
                else if (isCategory())
                    return type.cast(getCategory().getClassification());
                else
                    return null;
            }
            else if (type == InvestmentVehicle.class)
            {
                if (isSecurity())
                    return type.cast(getSecurity());
                else if (isAccount())
                    return type.cast(getAccount());
                else
                    return null;
            }
            else if (type == Account.class || type == TransactionOwner.class)
            {
                return isAccount() ? type.cast(getAccount()) : null;
            }
            else
            {
                return null;
            }
        }
    }

    public static class ElementComparator implements Comparator<Object>
    {
        private Comparator<Object> comparator;

        public ElementComparator(Comparator<Object> wrapped)
        {
            this.comparator = wrapped;
        }

        @Override
        public int compare(Object o1, Object o2)
        {
            int a = ((Element) o1).getSortOrder();
            int b = ((Element) o2).getSortOrder();

            if (a != b)
            {
                int direction = ColumnViewerSorter.SortingContext.getSortDirection();
                return direction == SWT.UP ? a - b : b - a;
            }

            return comparator.compare(o1, o2);
        }
    }

    /* testing */ static class ElementValueProvider
    {
        private final Function<LazySecurityPerformanceRecord, LazyValue<?>> valueProvider;
        private final Function<Stream<Object>, Object> collector;
        private final Function<PerformanceIndex, ?> valueProviderTotal;

        public ElementValueProvider(Function<LazySecurityPerformanceRecord, LazyValue<?>> valueProvider,
                        Function<Stream<Object>, Object> collector)
        {
            this.valueProvider = valueProvider;
            this.collector = collector;
            this.valueProviderTotal = null;
        }

        public ElementValueProvider(Function<LazySecurityPerformanceRecord, LazyValue<?>> valueProvider,
                        Function<Stream<Object>, Object> collector, Function<PerformanceIndex, ?> valueProviderTotal)
        {
            this.valueProvider = valueProvider;
            this.collector = collector;
            this.valueProviderTotal = valueProviderTotal;
        }

        public Object getValue(Element element, String currencyCode, Interval interval)
        {
            if (element.isSecurity())
            {
                // assumption: performance record has been calculated before!
                LazySecurityPerformanceRecord record = element.getPerformance(currencyCode, interval);

                // record is null if there are no transactions for the security
                // in the given period
                if (record == null)
                    return null;

                Object value = valueProvider.apply(record).get();

                // if not a monetary value, no splitting is supported
                if (!(value instanceof Money))
                    return value;

                // check if asset has been split across multiple categories

                // problem: we cannot use the "shares held" of the current
                // record, because the record can have a different reporting
                // period than the point in time of this particular snapshot
                // (for example the snapshot is from today, but the reporting
                // period is is for the year 2000). Therefore the "shares held"
                // given in the record can be different due to other
                // transactions.

                long positionShares = element.getPosition().getPosition().getShares();

                long totalShares = element.getGroupByTaxonomy().getCategories().flatMap(c -> c.getPositions().stream())
                                .filter(p -> element.getSecurity().equals(p.getSecurity()))
                                .mapToLong(p -> p.getPosition().getShares()).sum();

                if (positionShares != totalShares)
                {
                    Money moneyValue = (Money) value;
                    return Money.of(moneyValue.getCurrencyCode(),
                                    Math.round(moneyValue.getAmount() * positionShares / (double) totalShares));
                }
                else
                {
                    return value;
                }
            }
            else if (element.isCategory())
            {
                if (collector != null)
                    return collectValue(element.getChildren(), currencyCode, interval);
                else if (valueProviderTotal != null && !Objects.equals(Classification.UNASSIGNED_ID,
                                element.getCategory().getClassification().getId()))
                    return valueProviderTotal.apply(element.getPerformanceForCategoryTotals(currencyCode, interval));

                return null;
            }
            else if (element.isGroupByTaxonomy())
            {
                if (collector != null)
                    return collectValue(element.getChildren().flatMap(Element::getChildren), currencyCode, interval);
                else if (valueProviderTotal != null)
                    return valueProviderTotal.apply(element.getPerformanceForCategoryTotals(currencyCode, interval));

                return null;
            }
            else
            {
                return null;
            }
        }

        private Object collectValue(Stream<Element> elements, String currencyCode, Interval interval)
        {
            return collector.apply(elements.filter(Element::isSecurity) //
                            .map(child -> getValue(child, currencyCode, interval)) //
                            .filter(Objects::nonNull));
        }
    }

    private final class ReportingPeriodLabelProvider extends OptionLabelProvider<ReportingPeriod>
                    implements Comparator<Object>
    {
        private boolean showColorAndArrows;
        private ElementValueProvider valueProvider;
        private Function<Element, String> currencyProvider;

        public ReportingPeriodLabelProvider(Function<LazySecurityPerformanceRecord, LazyValue<?>> valueProvider)
        {
            this(new ElementValueProvider(valueProvider, null), null, true);
        }

        public ReportingPeriodLabelProvider(Function<LazySecurityPerformanceRecord, LazyValue<?>> valueProvider,
                        Function<Stream<Object>, Object> collector, boolean showUpAndDownArrows)
        {
            this(new ElementValueProvider(valueProvider, collector), null, showUpAndDownArrows);
        }

        public ReportingPeriodLabelProvider(Function<LazySecurityPerformanceRecord, LazyValue<?>> valueProvider,
                        boolean showUpAndDownArrows, Function<PerformanceIndex, ?> valueProviderTotal)
        {
            this(new ElementValueProvider(valueProvider, null, valueProviderTotal), null, showUpAndDownArrows);
        }

        public ReportingPeriodLabelProvider(ElementValueProvider valueProvider, boolean showUpAndDownArrows)
        {
            this(valueProvider, null, showUpAndDownArrows);
        }

        public ReportingPeriodLabelProvider(ElementValueProvider valueProvider,
                        Function<Element, String> currencyProvider, boolean showUpAndDownArrows)
        {
            this.valueProvider = valueProvider;
            this.currencyProvider = currencyProvider;
            this.showColorAndArrows = showUpAndDownArrows;
        }

        private Object getValue(Object e, ReportingPeriod option)
        {
            // every value is retrieved from a SecurityPerformanceRecord (the
            // logic for that is inside the ElementValueProvider class)

            // the SecurityPerformanceRecord are determined based on this logic:
            // - if given as an option to column (i.e. the user chooses an
            // interval explicitly), then this interval is used
            // - if no option is present, then the "global interval" is used,
            // i.e. an interval that includes all transaction until the chosen
            // date

            // the currency is either determined by the currencyProvider
            // (retrieves it from the security) or the default baseCurrency is
            // used

            Element element = (Element) e;

            // the period is calculated relative to the date of the snapshot
            Interval interval = option != null ? option.toInterval(model.getDate()) : model.getGlobalInterval();

            String currencyCode = currencyProvider != null ? currencyProvider.apply(element)
                            : model.getCurrencyConverter().getTermCurrency();

            model.calculatePerformanceAndInjectIntoElements(currencyCode, interval);

            return valueProvider.getValue(element, currencyCode, interval);
        }

        @Override
        public String getText(Object e, ReportingPeriod option)
        {
            Object value = getValue(e, option);
            if (value == null)
                return null;

            if (value instanceof Money money)
                return Values.Money.format(money, client.getBaseCurrency());
            else if (value instanceof Quote quote)
                return Values.CalculatedQuote.format(quote, client.getBaseCurrency());
            else if (value instanceof Double d)
                return Values.Percent2.format(d);

            return null;
        }

        @Override
        public Color getForeground(Object e, ReportingPeriod option)
        {
            if (!showColorAndArrows)
                return null;

            Object value = getValue(e, option);
            if (value == null)
                return null;

            double doubleValue = 0;
            if (value instanceof Money money)
                doubleValue = money.getAmount();
            else if (value instanceof Quote quote)
                doubleValue = quote.getAmount();
            else if (value instanceof Double d)
                doubleValue = d;

            if (doubleValue < 0)
                return Colors.theme().redForeground();
            else if (doubleValue > 0)
                return Colors.theme().greenForeground();
            else
                return null;
        }

        @Override
        public Image getImage(Object element, ReportingPeriod option)
        {
            if (!showColorAndArrows)
                return null;

            Object value = getValue(element, option);
            if (value == null)
                return null;

            double doubleValue = 0;
            if (value instanceof Money money)
                doubleValue = money.getAmount();
            else if (value instanceof Quote quote)
                doubleValue = quote.getAmount();
            else if (value instanceof Double d)
                doubleValue = d;

            if (doubleValue > 0)
                return Images.GREEN_ARROW.image();
            if (doubleValue < 0)
                return Images.RED_ARROW.image();
            return null;
        }

        @Override
        public Font getFont(Object e, ReportingPeriod option)
        {
            return ((Element) e).isGroupByTaxonomy() || ((Element) e).isCategory() ? boldFont : null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public int compare(Object o1, Object o2)
        {
            ReportingPeriod option = (ReportingPeriod) ColumnViewerSorter.SortingContext.getColumnOption();

            Comparable<Object> v1 = (Comparable<Object>) getValue(o1, option);
            Comparable<Object> v2 = (Comparable<Object>) getValue(o2, option);

            if (v1 == null && v2 == null)
                return 0;
            else if (v1 == null)
                return -1;
            else if (v2 == null)
                return 1;

            return v1.compareTo(v2);
        }
    }
}
