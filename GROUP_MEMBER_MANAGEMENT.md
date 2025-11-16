# Group Member Management - Implementation Guide

## Overview

This document describes the implementation of the two-tier user addition system for group management. The system provides secure member management with email autocomplete protection and mock email functionality for development.

## Architecture Decision

### Service Implementation Location

**Frontend:**
- `src/services/groupService.ts` - Group member management API calls
- `src/services/emailService.ts` - Email invitation handling (with dev mock)

**Backend:**
- `quizz-group-service` (recommended) - Dedicated group management microservice
- Alternative: Extend existing `quizz-user-service` with group endpoints

## Two-Tier System Design

### Tab 1: Add Existing User (Safe Autocomplete)

**Security Features:**
- ✅ Rate limiting: 10 requests/minute for group admins
- ✅ Minimum 3 characters required for search
- ✅ Scope-based filtering:
  - **App Admins**: Search all platform users (`all-users` scope)
  - **Group Admins**: Search only users from their groups (`my-groups` scope)
- ✅ Excludes existing group members from results
- ✅ Audit logging for security monitoring (backend)

**User Experience:**
- Debounced search (300ms delay)
- Loading indicators
- Real-time rate limit warnings
- Clear error messages
- Avatar display with fallback initials

### Tab 2: Invite by Email (No Autocomplete)

**Security Features:**
- ✅ No autocomplete (prevents user enumeration)
- ✅ Email validation (client + server)
- ✅ Invitation expiration (7 days)
- ✅ Mock mode for development

**User Experience:**
- Free-text email input
- Optional personal message (500 chars max)
- Role selection (MEMBER/ADMIN)
- Dev mode banner when mocked
- Console logging of mock invitations

## Implementation Files

### TypeScript Types
**File:** `src/types/group.types.ts`
```typescript
- GroupRole ('ADMIN' | 'MEMBER')
- AppRole ('APP_ADMIN' | 'USER')
- InvitationStatus ('PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED')
- UserSearchScope ('all-users' | 'my-groups' | 'same-domain')
- UserSearchResult, GroupMember, Group, EmailInvitation
- AddMemberPayload, InviteByEmailPayload, UserSearchFilters
- RateLimitInfo, GroupPermissions
```

### Services

#### Group Service
**File:** `src/services/groupService.ts`

**Key Methods:**
```typescript
// User Search & Addition
searchUsersForGroup(filters: UserSearchFilters): Promise<UserSearchResult[]>
  - Scoped user search with rate limiting
  - Excludes existing members
  - Minimum 3 characters enforced

addMemberToGroup(groupId: number, payload: AddMemberPayload): Promise<GroupMember>
  - Add existing user to group
  - Requires userId and role

inviteUserByEmail(groupId: number, payload: InviteByEmailPayload): Promise<EmailInvitation>
  - Send email invitation
  - Mocked in development

// Member Management
getGroupMembers(groupId: number): Promise<GroupMember[]>
  - Get all group members (non-paginated)

getGroupMembersPaginated(groupId, page, size, sort): Promise<PageResponse<GroupMember>>
  - Get group members with pagination
  - Default: page=0, size=10, sort='joinedAt'

removeMemberFromGroup(groupId: number, memberId: number): Promise<void>
  - Remove member from group (admin only)

updateMemberRole(groupId: number, memberId: number, role: GroupRole): Promise<GroupMember>
  - Update member's role (promote/demote)
  - Admin only

// Group Management
getMyGroups(): Promise<Group[]>
  - Get all groups where current user is a member

getGroup(groupId: number): Promise<Group>
  - Get single group details

getGroupPermissions(groupId: number): Promise<GroupPermissions>
  - Get user's permissions for a group

getUserSearchScope(appRole: AppRole): UserSearchScope
  - Helper to determine search scope based on user role
```

#### Email Service
**File:** `src/services/emailService.ts`

**Key Features:**
```typescript
class EmailService {
  private mockMode: boolean // Automatically set based on import.meta.env.DEV

  sendGroupInvitation(groupId, payload): Promise<EmailInvitation>
    - In dev: Mocks email sending (logs to console)
    - In prod: Delegates to groupService.inviteUserByEmail

  isMockMode(): boolean
    - Check if running in mock mode

  getMockModeStatus(): string
    - Get human-readable status
}

Helpers:
- isValidEmail(email: string): boolean
- getEmailDomain(email: string): string
```

### React Components

#### AddMemberModal
**File:** `src/components/groups/AddMemberModal.tsx`

**Purpose:** Main modal with two-tab interface

**Props:**
```typescript
interface AddMemberModalProps {
  groupId: number
  groupName: string
  currentUserRole: AppRole
  onMemberAdded: (member: GroupMember) => void
  onInviteSent: (invitation: EmailInvitation) => void
  onClose: () => void
}
```

**Features:**
- Tab switching (search vs invite)
- Modal backdrop with click-to-close
- Responsive design
- Context-aware messaging based on user role

#### UserSearchAutocomplete
**File:** `src/components/groups/UserSearchAutocomplete.tsx`

**Purpose:** Safe user search with autocomplete

**Props:**
```typescript
interface UserSearchAutocompleteProps {
  groupId: number
  currentUserRole: AppRole
  onMemberAdded: (member: GroupMember) => void
}
```

**Features:**
- Debounced search (300ms)
- Minimum 3 characters enforced
- Rate limit warnings
- Loading states
- User avatars with fallback
- Role selection (MEMBER/ADMIN)
- Security info display
- Click-outside to close dropdown

#### EmailInviteForm
**File:** `src/components/groups/EmailInviteForm.tsx`

**Purpose:** Email invitation with mock support

**Props:**
```typescript
interface EmailInviteFormProps {
  groupId: number
  onInviteSent: (invitation: EmailInvitation) => void
}
```

**Features:**
- Email validation
- Personal message (500 chars max)
- Role selection
- Mock mode banner
- Success/error feedback
- Security notes display
- Character counter for message

#### GroupList
**File:** `src/components/groups/GroupList.tsx`

**Purpose:** Display all groups where user is a member

**Props:**
```typescript
interface GroupListProps {
  onGroupSelect: (groupId: number) => void
  currentUserId?: number
}
```

**Features:**
- Grid layout of group cards
- Member count display
- Created date
- Click to view members
- Loading and error states
- Empty state message
- Responsive design

#### GroupMemberList
**File:** `src/components/groups/GroupMemberList.tsx`

**Purpose:** Paginated member list with admin actions

**Props:**
```typescript
interface GroupMemberListProps {
  groupId: number
  groupName: string
  currentUserId: number
  onBack?: () => void
}
```

**Features:**
- Server-side pagination (10 members per page)
- Member table with avatar, email, role, joined date
- Admin actions (visible to admins only):
  - Promote to admin
  - Demote to member
  - Remove from group
- Current user highlighting
- Confirmation dialogs for actions
- Loading states
- Error handling
- Responsive table design

## Backend API Specification

### Required Endpoints

#### 1. Search Users for Group
```http
GET /api/groups/{groupId}/search-users
Query Parameters:
  - query: string (min 3 chars)
  - scope: 'all-users' | 'my-groups'
  - excludeMembers: boolean (default: true)

Response: 200 OK
[
  {
    "id": 1,
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "avatar": "https://..." // optional
  }
]

Headers:
  X-RateLimit-Remaining: 9
  X-RateLimit-Reset: 2025-11-16T12:00:00Z

Error Responses:
  - 400: Invalid query (< 3 chars)
  - 403: Insufficient permissions
  - 429: Rate limit exceeded
```

**Security Requirements:**
- Rate limit: 10 requests/minute per user
- Scope enforcement:
  - `all-users`: Only for APP_ADMIN role
  - `my-groups`: Only users from requester's groups
- Exclude existing group members when excludeMembers=true
- Audit log all searches

#### 2. Add Member to Group
```http
POST /api/groups/{groupId}/members
Body:
{
  "userId": 123,
  "role": "MEMBER" | "ADMIN"
}

Response: 201 Created
{
  "id": 456,
  "userId": 123,
  "groupId": 789,
  "role": "MEMBER",
  "user": {
    "id": 123,
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "avatar": "https://..."
  },
  "joinedAt": "2025-11-16T10:00:00Z",
  "addedBy": 1
}

Error Responses:
  - 400: Invalid userId or role
  - 403: Insufficient permissions
  - 404: User or group not found
  - 409: User already in group
```

**Authorization:**
- APP_ADMIN: Can add any user
- GROUP_ADMIN: Can add users from their groups

#### 3. Invite User by Email
```http
POST /api/groups/{groupId}/invitations
Body:
{
  "email": "newuser@example.com",
  "message": "Join our group!",
  "role": "MEMBER" | "ADMIN"
}

Response: 201 Created
{
  "id": 789,
  "groupId": 456,
  "email": "newuser@example.com",
  "message": "Join our group!",
  "status": "PENDING",
  "invitedBy": 1,
  "createdAt": "2025-11-16T10:00:00Z",
  "expiresAt": "2025-11-23T10:00:00Z" // 7 days
}

Error Responses:
  - 400: Invalid email or message too long
  - 403: Insufficient permissions
  - 404: Group not found
  - 429: Too many invitations
```

**Email Template (Production):**
```
Subject: You're invited to join [Group Name]

Hi,

You've been invited to join the group "[Group Name]" on [Platform Name].

Personal message from [Inviter Name]:
"[Custom Message]"

Click here to accept the invitation:
[Invitation Link]

This invitation expires in 7 days.

Best regards,
[Platform Name] Team
```

#### 4. Get Group Members (Non-Paginated)
```http
GET /api/groups/{groupId}/members

Response: 200 OK
[
  {
    "id": 1,
    "userId": 123,
    "groupId": 789,
    "role": "ADMIN",
    "user": {
      "id": 123,
      "email": "user@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "avatar": "https://..."
    },
    "joinedAt": "2025-11-16T10:00:00Z",
    "addedBy": 1
  }
]
```

**Note:** Use for small groups or when you need all members at once.

#### 5. Get Group Members (Paginated)
```http
GET /api/groups/{groupId}/members/paginated
Query Parameters:
  - page: number (0-indexed, default: 0)
  - size: number (default: 10)
  - sort: string (default: joinedAt)

Response: 200 OK
{
  "content": [
    {
      "id": 1,
      "userId": 123,
      "groupId": 789,
      "role": "ADMIN",
      "user": {
        "id": 123,
        "email": "user@example.com",
        "firstName": "John",
        "lastName": "Doe",
        "avatar": "https://..."
      },
      "joinedAt": "2025-11-16T10:00:00Z",
      "addedBy": 1
    }
  ],
  "totalElements": 45,
  "totalPages": 5,
  "number": 0,
  "size": 10,
  "first": true,
  "last": false,
  "empty": false
}

Error Responses:
  - 403: Insufficient permissions
  - 404: Group not found
```

**Note:** Recommended for groups with many members. Uses Spring Data Page structure.

#### 6. Update Member Role (Promote/Demote)
```http
PUT /api/groups/{groupId}/members/{memberId}
Body:
{
  "role": "ADMIN" | "MEMBER"
}

Response: 200 OK
{
  "id": 456,
  "userId": 123,
  "groupId": 789,
  "role": "ADMIN",
  "user": { ... },
  "joinedAt": "2025-11-16T10:00:00Z",
  "addedBy": 1
}

Error Responses:
  - 400: Invalid role
  - 403: Insufficient permissions (not a group admin)
  - 404: Member or group not found
  - 409: Cannot change own role
```

**Authorization:**
- GROUP_ADMIN or APP_ADMIN only
- Cannot change own role
- Audit logging required

**Business Rules:**
- Group must have at least one admin
- Cannot demote the only admin
- Cannot modify role if user is the creator (optional)

#### 7. Remove Member from Group
```http
DELETE /api/groups/{groupId}/members/{memberId}

Response: 204 No Content

Error Responses:
  - 403: Insufficient permissions (not a group admin)
  - 404: Member or group not found
  - 409: Cannot remove self or last admin
```

**Authorization:**
- GROUP_ADMIN or APP_ADMIN only
- Cannot remove self
- Cannot remove last admin
- Audit logging required

**Business Rules:**
- Group must have at least one member (the admin)
- Last admin cannot be removed
- User can leave group voluntarily (separate endpoint recommended)

#### 8. Get My Groups
```http
GET /api/groups/my-groups

Response: 200 OK
[
  {
    "id": 1,
    "name": "Quiz Masters",
    "description": "For advanced quiz creators",
    "createdBy": 5,
    "createdAt": "2025-01-01T10:00:00Z",
    "updatedAt": "2025-11-16T10:00:00Z",
    "memberCount": 25
  }
]

Error Responses:
  - 401: Not authenticated
```

**Note:** Returns all groups where the current user is a member (any role).

#### 9. Get Single Group
```http
GET /api/groups/{groupId}

Response: 200 OK
{
  "id": 1,
  "name": "Quiz Masters",
  "description": "For advanced quiz creators",
  "createdBy": 5,
  "createdAt": "2025-01-01T10:00:00Z",
  "updatedAt": "2025-11-16T10:00:00Z",
  "memberCount": 25
}

Error Responses:
  - 403: Not a member of this group
  - 404: Group not found
```

#### 10. Get Group Permissions
```http
GET /api/groups/{groupId}/permissions

Response: 200 OK
{
  "canAddMembers": true,
  "canRemoveMembers": true,
  "canChangeRoles": true,
  "canInviteByEmail": true,
  "canSearchAllUsers": false
}

Error Responses:
  - 403: Not a member of this group
  - 404: Group not found
```

**Note:** Returns permissions for current user in specified group.

#### 11. Rate Limit Info
```http
GET /api/groups/{groupId}/rate-limit

Response: 200 OK
{
  "remaining": 8,
  "resetAt": "2025-11-16T12:00:00Z"
}
```

## Security Implementation Checklist

### Frontend Security
- ✅ Minimum 3 characters for search
- ✅ Debounced requests (300ms)
- ✅ Client-side email validation
- ✅ No autocomplete on email invite
- ✅ Rate limit warnings
- ✅ Message length validation (500 chars)

### Backend Security (Required)
- ⚠️ Rate limiting (10 requests/min per user)
- ⚠️ Scope enforcement (all-users vs my-groups)
- ⚠️ Permission checks on all endpoints
- ⚠️ Email validation (server-side)
- ⚠️ Audit logging for searches
- ⚠️ Invitation expiration (7 days)
- ⚠️ CSRF protection
- ⚠️ SQL injection prevention
- ⚠️ XSS prevention in email content

## Development Mode

### Mock Email Service

**Activation:** Automatic in `import.meta.env.DEV`

**Behavior:**
```javascript
// Console output when sending invitation:
🔧 DEV MODE: Mocking email invitation
📧 Email Details: {
  to: "user@example.com",
  groupId: 123,
  role: "MEMBER",
  message: "Join our group!"
}
✅ Mock invitation created: { id: 1234, ... }
📬 In production, an email would be sent to: user@example.com
📝 Email content would include:
   - Invitation link to join the group
   - Custom message: "Join our group!"
   - Role: MEMBER
   - Expiration: 7 days from now
```

**Benefits:**
- No email service required in dev
- Instant feedback in console
- Same API interface as production
- Easy to test invitation flow

### Switching to Production

**Automatic:** Based on `import.meta.env.DEV`
- Dev mode: Mock email service
- Production mode: Real email via backend API

**No code changes required** - environment detection is automatic.

## Usage Examples

### Example 1: Group List View
```typescript
import { GroupList, GroupMemberList } from '@/components/groups'

function MyGroupsPage() {
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null)
  const [selectedGroupName, setSelectedGroupName] = useState<string>('')
  const currentUserId = 123 // Get from auth context

  const handleGroupSelect = (groupId: number) => {
    // Fetch group name or pass it from group data
    setSelectedGroupId(groupId)
    setSelectedGroupName('Group Name')
  }

  const handleBack = () => {
    setSelectedGroupId(null)
  }

  return (
    <div>
      {!selectedGroupId ? (
        <GroupList
          onGroupSelect={handleGroupSelect}
          currentUserId={currentUserId}
        />
      ) : (
        <GroupMemberList
          groupId={selectedGroupId}
          groupName={selectedGroupName}
          currentUserId={currentUserId}
          onBack={handleBack}
        />
      )}
    </div>
  )
}
```

### Example 2: Add Member Modal
```typescript
import { AddMemberModal } from '@/components/groups'

function GroupManagement() {
  const [showModal, setShowModal] = useState(false)

  const handleMemberAdded = (member: GroupMember) => {
    console.log('Member added:', member)
    // Refresh member list
  }

  const handleInviteSent = (invitation: EmailInvitation) => {
    console.log('Invitation sent:', invitation)
    // Show success message
  }

  return (
    <>
      <button onClick={() => setShowModal(true)}>
        Add Members
      </button>

      {showModal && (
        <AddMemberModal
          groupId={123}
          groupName="My Group"
          currentUserRole="GROUP_ADMIN"
          onMemberAdded={handleMemberAdded}
          onInviteSent={handleInviteSent}
          onClose={() => setShowModal(false)}
        />
      )}
    </>
  )
}
```

### Example 3: Complete Group Management Flow
```typescript
import { GroupList, GroupMemberList, AddMemberModal } from '@/components/groups'

function CompleteGroupManagement() {
  const [view, setView] = useState<'list' | 'members'>('list')
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null)
  const [selectedGroupName, setSelectedGroupName] = useState<string>('')
  const [showAddModal, setShowAddModal] = useState(false)
  const currentUserId = 123 // Get from auth context
  const currentUserRole = 'GROUP_ADMIN' // Get from auth context or group permissions

  const handleGroupSelect = (groupId: number) => {
    setSelectedGroupId(groupId)
    setView('members')
  }

  const handleBack = () => {
    setView('list')
    setSelectedGroupId(null)
  }

  const handleMemberAdded = (member: GroupMember) => {
    // Refresh member list or update state
    setShowAddModal(false)
  }

  const handleInviteSent = (invitation: EmailInvitation) => {
    // Show success notification
    setShowAddModal(false)
  }

  return (
    <div className="group-management">
      {view === 'list' && (
        <GroupList
          onGroupSelect={handleGroupSelect}
          currentUserId={currentUserId}
        />
      )}

      {view === 'members' && selectedGroupId && (
        <>
          <div className="member-actions">
            <button onClick={() => setShowAddModal(true)}>
              Add Members
            </button>
          </div>

          <GroupMemberList
            groupId={selectedGroupId}
            groupName={selectedGroupName}
            currentUserId={currentUserId}
            onBack={handleBack}
          />

          {showAddModal && (
            <AddMemberModal
              groupId={selectedGroupId}
              groupName={selectedGroupName}
              currentUserRole={currentUserRole}
              onMemberAdded={handleMemberAdded}
              onInviteSent={handleInviteSent}
              onClose={() => setShowAddModal(false)}
            />
          )}
        </>
      )}
    </div>
  )
}
```

## Testing Checklist

### Frontend Testing

#### Add Member Modal
- [ ] Search with < 3 characters (should not search)
- [ ] Search with 3+ characters (should show results)
- [ ] Rate limit warning display
- [ ] Tab switching between search and invite
- [ ] Add existing user flow
- [ ] Email invitation flow (check console in dev)
- [ ] Email validation (invalid format)
- [ ] Message length validation (> 500 chars)
- [ ] Role selection (MEMBER/ADMIN)
- [ ] Modal close on backdrop click
- [ ] Responsive design on mobile

#### Group List
- [ ] Display all user's groups
- [ ] Show correct member count
- [ ] Group card click navigates to member list
- [ ] Empty state when no groups
- [ ] Loading state while fetching
- [ ] Error handling and retry
- [ ] Responsive grid layout

#### Group Member List
- [ ] Paginated member list (10 per page)
- [ ] Navigate between pages
- [ ] Display member info (avatar, name, email, role, joined date)
- [ ] Highlight current user
- [ ] Promote member to admin (admin only)
- [ ] Demote admin to member (admin only)
- [ ] Remove member from group (admin only)
- [ ] Confirmation dialogs for actions
- [ ] Cannot perform actions on self
- [ ] Admin actions hidden for non-admins
- [ ] Back button returns to group list
- [ ] Responsive table on mobile

### Backend Testing

#### User Search
- [ ] Rate limiting enforcement
- [ ] Scope enforcement (app admin vs group admin)
- [ ] Exclude existing members from results
- [ ] Audit logging for searches

#### Member Management
- [ ] Add member with correct role
- [ ] Duplicate member prevention
- [ ] Update member role (promote/demote)
- [ ] Cannot change own role
- [ ] Cannot demote last admin
- [ ] Remove member from group
- [ ] Cannot remove self
- [ ] Cannot remove last admin
- [ ] Permission checks on all operations

#### Group Management
- [ ] Get user's groups (my-groups)
- [ ] Get single group details
- [ ] Get paginated member list
- [ ] Get group permissions
- [ ] Member count accuracy

#### Email Invitations
- [ ] Invitation expiration (7 days)
- [ ] Email sending (production)
- [ ] Invitation acceptance flow

## Future Enhancements

### Planned Features
1. **Same-Domain Scope**: Search users from same email domain
2. **Bulk Invite**: CSV upload for multiple invitations
3. **Invitation Templates**: Predefined message templates
4. **Invitation Analytics**: Track acceptance rates
5. **Custom Expiration**: Configurable expiration time
6. **Resend Invitation**: Resend expired invitations

### Security Enhancements
1. **2FA for Admin Actions**: Require 2FA for adding admins
2. **IP-based Rate Limiting**: Additional rate limiting by IP
3. **CAPTCHA**: For multiple failed searches
4. **Invitation Quota**: Limit invitations per user/group

## Troubleshooting

### Common Issues

**Issue:** Search not working
- Check minimum 3 characters
- Verify backend API is running
- Check network tab for errors
- Verify user has correct permissions

**Issue:** Rate limit warnings
- Wait 1 minute and try again
- Check backend rate limit configuration
- Verify rate limit headers in response

**Issue:** Email invitations not working in dev
- This is expected - check console for mock output
- Verify `import.meta.env.DEV` is true
- Check for error messages in console

**Issue:** Build errors
- Run `npm run build` to check TypeScript errors
- Verify all imports are correct
- Check for missing type definitions

## Documentation Links

- [FRONTEND_GROUP_REQUIREMENTS.md](./FRONTEND_GROUP_REQUIREMENTS.md) - Complete group feature requirements
- [API_INTEGRATION.md](./API_INTEGRATION.md) - Backend API integration guide
- [ARCHITECTURE.md](./ARCHITECTURE.md) - System architecture overview

## Support

For questions or issues:
1. Check this documentation
2. Review console errors
3. Check backend API logs
4. Create issue in repository
