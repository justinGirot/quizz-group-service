package com.quizz.group.service;

import com.quizz.group.dto.InvitationDTO;
import com.quizz.group.dto.InviteUserRequest;
import com.quizz.group.dto.MemberDTO;
import com.quizz.group.exception.BadRequestException;
import com.quizz.group.exception.GroupNotFoundException;
import com.quizz.group.exception.InvitationExpiredException;
import com.quizz.group.exception.InvitationNotFoundException;
import com.quizz.group.model.*;
import com.quizz.group.repository.GroupInvitationRepository;
import com.quizz.group.repository.GroupRepository;
import com.quizz.group.util.MapperUtil;
import com.quizz.group.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing group invitations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupInvitationService {

    private final GroupInvitationRepository invitationRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberService groupMemberService;

    private static final int INVITATION_EXPIRY_DAYS = 7;

    /**
     * Invite a user to a group.
     */
    @Transactional
    public InvitationDTO inviteUser(Long groupId, Long invitedBy, InviteUserRequest request) {
        log.info("Inviting user to group {}: email={}, userId={}", groupId, request.getEmail(), request.getUserId());

        // Validate group exists
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }

        // Validate that either email or userId is provided
        if ((request.getEmail() == null || request.getEmail().trim().isEmpty()) && request.getUserId() == null) {
            throw new BadRequestException("Either email or userId must be provided");
        }

        // Check if user is already a member (if userId provided)
        if (request.getUserId() != null && groupMemberService.isMember(groupId, request.getUserId())) {
            throw new BadRequestException("User is already a member of this group");
        }

        // Check if active invitation already exists
        if (invitationRepository.existsActiveInvitation(
                groupId,
                request.getEmail(),
                request.getUserId(),
                InvitationStatus.PENDING,
                LocalDateTime.now()
        )) {
            throw new BadRequestException("An active invitation already exists for this user");
        }

        // Generate unique token
        String token = generateUniqueToken();

        // Create invitation
        GroupInvitation invitation = GroupInvitation.builder()
                .groupId(groupId)
                .invitedBy(invitedBy)
                .inviteeEmail(request.getEmail())
                .inviteeUserId(request.getUserId())
                .status(InvitationStatus.PENDING)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(INVITATION_EXPIRY_DAYS))
                .build();

        GroupInvitation savedInvitation = invitationRepository.save(invitation);

        log.info("Invitation created successfully with token: {}", token);
        return MapperUtil.toInvitationDTO(savedInvitation);
    }

    /**
     * Accept an invitation.
     */
    @Transactional
    public MemberDTO acceptInvitation(String token, Long userId) {
        log.info("Accepting invitation with token: {}", token);

        GroupInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token, true));

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation has already been " + invitation.getStatus().name().toLowerCase());
        }

        // Check expiration
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new InvitationExpiredException("Invitation has expired");
        }

        // Validate user (if invitation was for specific userId)
        if (invitation.getInviteeUserId() != null && !invitation.getInviteeUserId().equals(userId)) {
            throw new BadRequestException("This invitation is for a different user");
        }

        // Check if user is already a member
        if (groupMemberService.isMember(invitation.getGroupId(), userId)) {
            throw new BadRequestException("User is already a member of this group");
        }

        // Update invitation status
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        // Add user as member
        groupMemberService.addMemberDirectly(
                invitation.getGroupId(),
                userId,
                MemberRole.MEMBER,
                MemberStatus.ACTIVE
        );

        // Increment member count
        groupRepository.incrementMemberCount(invitation.getGroupId());

        log.info("Invitation accepted successfully");
        return groupMemberService.getMemberDetails(invitation.getGroupId(), userId);
    }

    /**
     * Decline an invitation.
     */
    @Transactional
    public void declineInvitation(String token, Long userId) {
        log.info("Declining invitation with token: {}", token);

        GroupInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token, true));

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation has already been " + invitation.getStatus().name().toLowerCase());
        }

        // Check expiration
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new InvitationExpiredException("Invitation has expired");
        }

        // Validate user (if invitation was for specific userId)
        if (invitation.getInviteeUserId() != null && !invitation.getInviteeUserId().equals(userId)) {
            throw new BadRequestException("This invitation is for a different user");
        }

        // Update invitation status
        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setRespondedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        log.info("Invitation declined successfully");
    }

    /**
     * Get pending invitations for a user by email.
     */
    @Transactional(readOnly = true)
    public List<InvitationDTO> getPendingInvitationsByEmail(String email) {
        log.debug("Getting pending invitations for email: {}", email);

        return invitationRepository.findByInviteeEmailAndStatus(email, InvitationStatus.PENDING)
                .stream()
                .filter(invitation -> invitation.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(MapperUtil::toInvitationDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get pending invitations for a user by user ID.
     */
    @Transactional(readOnly = true)
    public List<InvitationDTO> getPendingInvitationsByUserId(Long userId) {
        log.debug("Getting pending invitations for user: {}", userId);

        return invitationRepository.findByInviteeUserIdAndStatus(userId, InvitationStatus.PENDING)
                .stream()
                .filter(invitation -> invitation.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(MapperUtil::toInvitationDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all invitations for a group.
     */
    @Transactional(readOnly = true)
    public List<InvitationDTO> getGroupInvitations(Long groupId) {
        log.debug("Getting invitations for group: {}", groupId);

        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }

        return invitationRepository.findByGroupIdAndStatus(groupId, InvitationStatus.PENDING)
                .stream()
                .map(MapperUtil::toInvitationDTO)
                .collect(Collectors.toList());
    }

    /**
     * Generate a unique invitation token.
     */
    private String generateUniqueToken() {
        String token;
        do {
            token = TokenGenerator.generateToken();
        } while (invitationRepository.findByToken(token).isPresent());
        return token;
    }

    /**
     * Mark expired invitations (scheduled task could call this).
     */
    @Transactional
    public void markExpiredInvitations() {
        log.info("Marking expired invitations");

        List<GroupInvitation> expiredInvitations = invitationRepository
                .findExpiredInvitations(InvitationStatus.PENDING, LocalDateTime.now());

        expiredInvitations.forEach(invitation -> {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
        });

        log.info("Marked {} invitations as expired", expiredInvitations.size());
    }
}
