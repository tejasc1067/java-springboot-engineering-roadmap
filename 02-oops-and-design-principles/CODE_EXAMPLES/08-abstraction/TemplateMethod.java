// Template Method pattern. The parent class defines the workflow as a `final`
// method. Subclasses customize the individual steps.
//
// Used everywhere in real frameworks (Spring's AbstractAuthenticationProcessingFilter,
// JDK's AbstractList, etc.).

abstract class ReportGenerator {

    // The workflow is fixed: fetch → format → save. Final so subclasses can't
    // change the order.
    public final void generate() {
        System.out.println("--- generating " + reportName() + " ---");
        Object data = fetchData();
        String formatted = format(data);
        save(formatted);
        System.out.println();
    }

    protected abstract String  reportName();
    protected abstract Object  fetchData();
    protected abstract String  format(Object data);
    protected abstract void    save(String formatted);
}

class PdfReport extends ReportGenerator {
    protected String reportName()              { return "PDF report"; }
    protected Object fetchData()               { System.out.println("  pdf: fetching"); return "data"; }
    protected String format(Object data)       { System.out.println("  pdf: formatting"); return "PDF[" + data + "]"; }
    protected void   save(String formatted)    { System.out.println("  pdf: saved " + formatted); }
}

class CsvReport extends ReportGenerator {
    protected String reportName()              { return "CSV report"; }
    protected Object fetchData()               { System.out.println("  csv: fetching"); return "row1,row2"; }
    protected String format(Object data)       { System.out.println("  csv: formatting"); return data.toString(); }
    protected void   save(String formatted)    { System.out.println("  csv: saved " + formatted); }
}

public class TemplateMethod {
    public static void main(String[] args) {
        new PdfReport().generate();
        new CsvReport().generate();
        // Caller only sees generate(). The workflow is consistent.
        // Each subclass picks what happens at each step.
    }
}
