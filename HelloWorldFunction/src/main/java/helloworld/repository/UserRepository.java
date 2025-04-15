package helloworld.repository;

import java.util.List;
import java.util.stream.Collectors;

import helloworld.User;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class UserRepository {

    private final DynamoDbTable<User> userTable;
    private final String tableName;

    public UserRepository() {
        this.tableName = System.getenv("USER_TABLE_NAME");
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        this.userTable = enhancedClient.table(tableName, TableSchema.fromBean(User.class));
    }

    public User getUserById(String userId) {
        return userTable.getItem(Key.builder().partitionValue(userId).build());
    }

    public User getUserByEmail(String email) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(email).build());
        
        List<User> users = userTable.index("EmailIndex")
                .query(queryConditional)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
        
        return users.isEmpty() ? null : users.get(0);
    }

    public void saveUser(User user) {
        userTable.putItem(user);
    }

    public void deleteUser(String userId) {
        userTable.deleteItem(Key.builder().partitionValue(userId).build());
    }

    public List<User> getAllUsers() {
        return userTable.scan().items().stream().collect(Collectors.toList());
    }

    public User getAdminUser() {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("role = :role")
                        .putExpressionValue(":role", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s("admin").build())
                        .build())
                .build();
        
        List<User> admins = userTable.scan(scanRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
        
        return admins.isEmpty() ? null : admins.get(0);
    }
}