package helloworld.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import helloworld.Task;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;


public class TaskRepository {

    private final DynamoDbTable<Task> taskTable;
    private final String tableName;

    public TaskRepository() {
        this.tableName = System.getenv("TASK_TABLE_NAME");
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        this.taskTable = enhancedClient.table(tableName, TableSchema.fromBean(Task.class));
    }

    public Task getTask(String taskId) {
        return taskTable.getItem(Key.builder().partitionValue(taskId).build());
    }

    public void saveTask(Task task) {
        taskTable.putItem(task);
    }

    public void deleteTask(String taskId) {
        taskTable.deleteItem(Key.builder().partitionValue(taskId).build());
    }

    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>();
        taskTable.scan().forEach(page -> tasks.addAll(page.items()));
        return tasks;
    }

    public List<Task> getTasksByAssignee(String userId) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(userId).build());
        
        return taskTable.index("AssignedToIndex")
                .query(queryConditional)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<Task> getTasksByStatus(String status) {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .putExpressionValue(":status", AttributeValue.builder().s(status).build())
                        .putExpressionValue(":status", AttributeValue.builder().s(status).build())
                        .build())
                .build();
        
        return taskTable.scan(scanRequest)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<Task> getTasksByDeadlineBefore(String deadlineTimestamp) {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .putExpressionValue(":deadline", AttributeValue.builder().s(deadlineTimestamp).build())
                        .putExpressionValue(":deadline", AttributeValue.builder().s(deadlineTimestamp).build())
                        .build())
                .build();
        
        return taskTable.scan(scanRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
    }
}