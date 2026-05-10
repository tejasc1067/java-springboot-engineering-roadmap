class ExamResult {

    private int marks;

    public void setMarks(int marks) {

        if (marks >= 0 && marks <= 100) {

            this.marks = marks;
        }
        else {

            System.out.println(
                    "Invalid Marks Entered"
            );
        }
    }

    public int getMarks() {

        return marks;
    }
}

public class ValidationSetterExample {

    public static void main(String[] args) {

        ExamResult result = new ExamResult();

        result.setMarks(85);

        System.out.println(
                "Marks: "
                        + result.getMarks()
        );

        result.setMarks(-10);
    }
}