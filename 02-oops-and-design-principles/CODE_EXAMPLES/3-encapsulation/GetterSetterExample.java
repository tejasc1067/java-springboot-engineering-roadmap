class Student {

    private int id;

    private String name;

    public int getId() {

        return id;
    }

    public void setId(int id) {

        this.id = id;
    }

    public String getName() {

        return name;
    }

    public void setName(String name) {

        this.name = name;
    }
}

public class GetterSetterExample {

    public static void main(String[] args) {

        Student student = new Student();

        student.setId(101);

        student.setName("Tejas");

        System.out.println(
                "ID: "
                        + student.getId()
        );

        System.out.println(
                "Name: "
                        + student.getName()
        );
    }
}