package helloworld;

public class TaskUpdateRequest {
    private String status;
    private String comment;

    public TaskUpdateRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "TaskUpdateRequest{" +
                "status='" + status + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}