package name.abuchen.portfolio.datatransfer.pdf;

import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Block;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.DocumentType;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Transaction;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.AccountTransaction.Type;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.Money;

@SuppressWarnings("nls")
public class AkfBankPDFExtractor extends AbstractPDFExtractor
{
    public AkfBankPDFExtractor(Client client)
    {
        super(client);

        addBankIdentifier("akf bank GmbH & Co KG");

        addAccountStatementTransaction();
    }

    @Override
    public String getLabel()
    {
        return "akf bank GmbH & Co KG";
    }

    private void addAccountStatementTransaction()
    {
        final DocumentType type = new DocumentType("akf bank", //
                        documentContext -> documentContext //
                                        // @formatter:off
                                        // Nr. Valuta / Buchungstag Buchungstext EUR
                                        // @formatter:on
                                        .section("currency") //
                                        .match("^Nr\\. Valuta \\/ Buchungstag Buchungstext (?<currency>[\\w]{3}).*$") //
                                        .assign((ctx, v) -> ctx.put("currency", asCurrencyCode(v.get("currency")))));

        this.addDocumentTyp(type);

        // @formatter:off
        // 01 08.04.2011 / 08.04.2011 Gutschrift 29.000,12
        // 01 16.08.2022 / 16.08.2022 SEPA Gutschrift Bank 755,00
        // @formatter:on
        Block depositBlock = new Block("^[\\d]{2} [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} (Gutschrift|SEPA Gutschrift( Bank)?) .*$");
        type.addBlock(depositBlock);
        depositBlock.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.DEPOSIT);
                            return accountTransaction;
                        })

                        .section("date", "note", "amount") //
                        .documentContext("currency") //
                        .match("^[\\d]{2} (?<date>[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}) \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} " //
                                        + "(?<note>(Gutschrift" //
                                        + "|SEPA Gutschrift( Bank)?)) " //
                                        + "(?<amount>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            t.setDateTime(asDate(v.get("date")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(v.get("currency"));
                            t.setNote(v.get("note"));
                        })

                        .wrap(TransactionItem::new));

        // @formatter:off
        // 02 05.12.2012 / 04.12.2012 DTA Überweisung -1.660,89
        // 01 13.04.2015 / 11.04.2015 SEPA Überweisung online -1,24
        //
        // 01 14.11.2012 / 14.11.2012 Einzelüberweisung -5,00
        // Gebühren für Nachbe-
        // @formatter:on
        Block removalBlock = new Block("^[\\d]{2} [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} "
                        + "(Einzel)?(.*berweisung.*"
                        + "|Festgeld Anlage"
                        + "|Sparkonto K.ndigung) \\-[\\.,\\d]+$");
        type.addBlock(removalBlock);
        removalBlock.setMaxSize(2);
        removalBlock.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.REMOVAL);
                            return accountTransaction;
                        })

                        .section("date", "note", "amount", "type") //
                        .documentContext("currency") //
                        .match("^[\\d]{2} (?<date>[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}) \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} " //
                                        + "(?<note>(Einzel)?(.*berweisung.*" //
                                        + "|Festgeld Anlage" //
                                        + "|Sparkonto K.ndigung)) " //
                                        + "\\-(?<amount>[\\.,\\d]+)$") //
                        .match("^(?<type>[^\\s]+).*$")
                        .assign((t, v) -> {
                            // @formatter:off
                            // Is type is "Gebühren" change from REMOVAL to FEES
                            // @formatter:on
                            if (v.get("type") != null && v.get("type").matches("Geb.hren"))
                                t.setType(Type.FEES);

                            t.setDateTime(asDate(v.get("date")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(v.get("currency"));
                            t.setNote(v.get("note"));
                        })

                        .wrap(TransactionItem::new));

        Block interestBlock = new Block("^[\\d]{2} [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} Kontoabschlu. [\\.,\\d]+$");
        type.addBlock(interestBlock);
        interestBlock.set(new Transaction<AccountTransaction>()

                        .subject(() -> {
                            AccountTransaction accountTransaction = new AccountTransaction();
                            accountTransaction.setType(AccountTransaction.Type.INTEREST);
                            return accountTransaction;
                        })

                        // @formatter:off
                        // 02 30.11.2012 / 30.11.2012 Kontoabschluß 2,71
                        // Habenzinsen
                        // v. 31.10.2012 b. 30.11.2012
                        // Zinsen zu 2,150
                        // @formatter:on
                        .section("date", "amount", "note1", "note2", "note3") //
                        .documentContext("currency") //
                        .match("^[\\d]{2} (?<date>[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}) \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} Kontoabschlu. (?<amount>[\\.,\\d]+)$") //
                        .match("^v\\. (?<note1>[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}) b\\. (?<note2>[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}).*$") //
                        .match("^Zinsen zu[\\s]{1,}(?<note3>[\\.,\\d]+).*$")
                        .assign((t, v) -> {
                            t.setDateTime(asDate(v.get("date")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setCurrencyCode(v.get("currency"));
                            t.setNote(v.get("note1") + " - " + v.get("note2") + " (" + v.get("note3") + " %)");
                        })

                        // @formatter:off
                        // 02 30.09.2023 / 30.09.2023 Kontoabschluß -0,25
                        // Abgeltungssteuer
                        // @formatter:on
                        .section("tax").optional() //
                        .documentContext("currency") //
                        .match("^[\\d]{2} [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} Kontoabschlu. \\-(?<tax>[\\.,\\d]+)$") //
                        .match("^Abgeltungssteuer.*$") //
                        .assign((t, v) -> {
                            Money tax = Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax")));
                            t.addUnit(new Unit(Unit.Type.TAX, tax));

                            t.setMonetaryAmount(t.getMonetaryAmount().subtract(tax));
                        })

                        // @formatter:off
                        // 03 30.09.2023 / 30.09.2023 Kontoabschluß -0,01
                        // Solidaritätszuschlag
                        // @formatter:on
                        .section("tax").optional() //
                        .documentContext("currency") //
                        .find("Abgeltungssteuer.*") //
                        .match("^[\\d]{2} (?<date>[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}) \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} Kontoabschlu. \\-(?<tax>[\\.,\\d]+)$") //
                        .match("^Solidarit.tszuschlag.*$") //
                        .assign((t, v) -> {
                            Money tax = Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax")));
                            t.addUnit(new Unit(Unit.Type.TAX, tax));

                            t.setMonetaryAmount(t.getMonetaryAmount().subtract(tax));
                        })

                        // @formatter:off
                        // 04 30.09.2023 / 30.09.2023 Kontoabschluß -0,02
                        // Kirchensteuer
                        // @formatter:on
                        .section("tax").optional() //
                        .documentContext("currency") //
                        .find("Solidarit.tszuschlag.*") //
                        .match("^[\\d]{2} (?<date>[\\d]{2}\\.[\\d]{2}\\.[\\d]{4}) \\/ [\\d]{2}\\.[\\d]{2}\\.[\\d]{4} Kontoabschlu. \\-(?<tax>[\\.,\\d]+)$") //
                        .match("^Kirchensteuer.*$") //
                        .assign((t, v) -> {
                            Money tax = Money.of(asCurrencyCode(v.get("currency")), asAmount(v.get("tax")));
                            t.addUnit(new Unit(Unit.Type.TAX, tax));

                            t.setMonetaryAmount(t.getMonetaryAmount().subtract(tax));
                        })

                        .wrap(TransactionItem::new));
    }
}
