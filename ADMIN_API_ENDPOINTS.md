# Admin API Endpoints

## User Management Endpoints

### 1. Get All Users (with pagination and search)
```
GET /api/v1/users?keyword={keyword}&page={page}&limit={limit}
```
**Authorization**: ADMIN role required
**Parameters**:
- `keyword` (optional): Search by full name
- `page` (default: 0): Page number (0-based)
- `limit` (default: 10): Number of items per page

**Response**:
```json
{
  "content": [
    {
      "id": 1,
      "fullname": "John Doe",
      "phone_number": "0123456789",
      "address": "123 Main St",
      "is_active": true,
      "date_of_birth": "1990-01-01T00:00:00.000+00:00",
      "facebook_account_id": 0,
      "google_account_id": 0,
      "role": {
        "id": 2,
        "name": "USER"
      }
    }
  ],
  "totalElements": 100,
  "totalPages": 10,
  "size": 10,
  "number": 0
}
```

### 2. Delete User
```
DELETE /api/v1/users/{userId}
```
**Authorization**: ADMIN role required
**Parameters**:
- `userId`: ID of the user to delete

**Response**:
```json
"User deleted successfully"
```

### 3. Update User Role
```
PUT /api/v1/users/role/{userId}
```
**Authorization**: ADMIN role required
**Parameters**:
- `userId`: ID of the user to update
- Body: String with new role ("ADMIN" or "USER")

**Response**:
```json
{
  "id": 1,
  "fullname": "John Doe",
  "phone_number": "0123456789",
  "address": "123 Main St",
  "is_active": true,
  "date_of_birth": "1990-01-01T00:00:00.000+00:00",
  "facebook_account_id": 0,
  "google_account_id": 0,
  "role": {
    "id": 1,
    "name": "ADMIN"
  }
}
```

## Security

All admin endpoints require ADMIN role authentication. The JWT token must be included in the Authorization header:

```
Authorization: Bearer {jwt_token}
```

## Database Changes

### UserRepository
Added method:
- `findByFullNameContainingIgnoreCase(String fullName, Pageable pageable)`

### RoleRepository
Added method:
- `findByName(String name)`

## Service Layer

### IUserService
Added methods:
- `getAllUsers(String keyword, Pageable pageable)`
- `deleteUser(Long userId)`
- `updateUserRole(Long userId, String newRole)`

### UserService Implementation
- Added pagination support for user listing
- Added search functionality by full name
- Added role update functionality
- Added user deletion with admin protection (cannot delete admin users)

## Error Handling

The endpoints return appropriate HTTP status codes:
- `200 OK`: Success
- `400 Bad Request`: Invalid input or business logic error
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: User not found
- `500 Internal Server Error`: Server error

## Frontend Integration

The frontend UserService has been updated to include:
- `getAllUsers(keyword, page, limit)`
- `deleteUser(userId)`
- `updateUserRole(userId, newRole)`

All methods use proper authentication headers and handle responses appropriately. 