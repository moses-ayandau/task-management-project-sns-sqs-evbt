package helloworld;
public class TaskReassignmentRequest {
    private String newAssignedTo;
    private Long newDeadline;

    public TaskReassignmentRequest() {
    }

    public String getNewAssignedTo() {
        return newAssignedTo;
    }

    public void setNewAssignedTo(String newAssignedTo) {
        this.newAssignedTo = newAssignedTo;
    }

    public Long getNewDeadline() {
        return newDeadline;
    }

    public void setNewDeadline(Long newDeadline) {
        this.newDeadline = newDeadline;
    }

    @Override
    public String toString() {
        return "TaskReassignmentRequest{" +
                "newAssignedTo='" + newAssignedTo + '\'' +
                ", newDeadline=" + newDeadline +
                '}';
    }
}