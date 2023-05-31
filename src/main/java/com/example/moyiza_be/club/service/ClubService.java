package com.example.moyiza_be.club.service;

import com.amazonaws.Response;
import com.example.moyiza_be.chat.service.ChatService;
import com.example.moyiza_be.club.dto.*;
import com.example.moyiza_be.club.entity.Club;
import com.example.moyiza_be.club.entity.ClubImageUrl;
import com.example.moyiza_be.club.entity.ClubJoinEntry;
import com.example.moyiza_be.club.repository.ClubImageUrlRepository;
import com.example.moyiza_be.club.repository.ClubJoinEntryRepository;
import com.example.moyiza_be.club.repository.ClubRepository;
import com.example.moyiza_be.common.enums.CategoryEnum;
import com.example.moyiza_be.common.enums.ChatTypeEnum;
import com.example.moyiza_be.common.utils.Message;
import com.example.moyiza_be.event.entity.Event;
import com.example.moyiza_be.event.repository.EventRepository;
import com.example.moyiza_be.event.service.EventService;
import com.example.moyiza_be.user.entity.User;
import com.example.moyiza_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubJoinEntryRepository clubJoinEntryRepository;
    private final EventService eventService;
    private final UserService userService;
    private final ClubImageUrlRepository clubImageUrlRepository;
    private final EventRepository eventRepository;
    private final ChatService chatService;


    //클럽 가입
    public ResponseEntity<Message> joinClub(Long clubId, User user) {
        if(clubJoinEntryRepository.existsByClubIdAndUserId(clubId, user.getId())){
            return new ResponseEntity<>(new Message("중복으로 가입할 수 없습니다"), HttpStatus.BAD_REQUEST);
        }
        ClubJoinEntry joinEntry = new ClubJoinEntry(user.getId(), clubId);
        clubJoinEntryRepository.save(joinEntry);
        //미래에 조건검증 추가
        Message message = new Message("가입이 승인되었습니다.");
        chatService.joinChat(clubId, ChatTypeEnum.CLUB, user);
        return ResponseEntity.ok(message);
    }

    //클럽 리스트 조회(전체조회, 검색조회 포함)
    public ResponseEntity<Page<ClubListResponse>> getClubList(Pageable pageable, CategoryEnum category, String q) {
        Page<ClubListResponse> responseList;
        if (category != null && q != null) {
            //카테고리와 검색어를 모두 입력한 경우
             responseList = clubRepository.findByCategoryAndIsDeletedFalseAndTitleContaining(pageable, category, q).map(ClubListResponse::new);
        } else if (category != null) {
            //카테고리만 입력한 경우
            responseList = clubRepository.findByCategoryAndIsDeletedFalse(pageable, category).map(ClubListResponse::new);
        } else if (q != null) {
            responseList = clubRepository.findByIsDeletedFalseAndTitleContaining(pageable, q).map(ClubListResponse::new);
        } else {
            //카테고리와 검색어가 모두 입력되지 않은 경우 전체 클럽 조회
            responseList = clubRepository.findAllByIsDeletedFalse(pageable).map(ClubListResponse::new);
        }
        return ResponseEntity.ok(responseList);
    }


    //클럽 상세 조회
    public ResponseEntity<ClubDetailResponse> getClubDetail(Long clubId) {
        Club club = loadClubByClubId(clubId);
        List<String> clubImageUrlList= clubImageUrlRepository.findAllByClubId(clubId).stream().map(ClubImageUrl::getImageUrl).toList();
        ClubDetailResponse responseDto = new ClubDetailResponse(club, clubImageUrlList);
        return ResponseEntity.ok(responseDto);
    }


    //클럽 멤버 조회
    //프로필사진, 닉네임, 클럽 가입 날짜
    public ResponseEntity<List<ClubMemberResponse>> getClubMember(Long clubId) {
        //queryDSL 적용 할 때 갈아엎어야함 (쿼리나가는거, 성능 계산해보고 결정)

        List<ClubJoinEntry> joinEntryList = clubJoinEntryRepository.findByClubId(clubId);
        Map<Long, LocalDateTime> joinEntryMap = new HashMap<>(); // inspection ??
        List<Long> userIdList = joinEntryList.stream()
                .peek(entry -> joinEntryMap.put(entry.getUserId(), entry.getCreatedAt()))
                .map(ClubJoinEntry::getUserId)
                .toList();

        List<User> memberList = userService.loadUserListByIdList(userIdList);

        List<ClubMemberResponse> clubMemberResponseList = memberList.stream()
                .map(member -> new ClubMemberResponse(member, joinEntryMap.get(member.getId())))
                .toList();

        return ResponseEntity.ok(clubMemberResponseList);
    }

    //클럽 탈퇴
    public ResponseEntity<Message> goodbyeClub(Long clubId, User user) {

        ClubJoinEntry joinEntry = clubJoinEntryRepository.findByUserIdAndClubId(user.getId(), clubId);
        if (joinEntry != null) {
            clubJoinEntryRepository.delete(joinEntry);
            Message message = new Message("클럽에서 탈퇴되었습니다.");
            chatService.leaveChat(clubId, ChatTypeEnum.CLUB, user);
            return ResponseEntity.ok(message);
        } else {
            Message message = new Message("클럽이 존재하지 않거나, 가입 정보가 없습니다.");
            return ResponseEntity.ok(message);
        }
    }

    //클럽 강퇴
    public ResponseEntity<Message> banClub(Long clubId, Long userId, BanRequest banRequest) {
        if(!clubRepository.existsByIdAndIsDeletedFalseAndOwnerIdEquals(clubId, userId)){
            return new ResponseEntity<>(new Message("권한이 없거나, 클럽이 없습니다"), HttpStatus.BAD_REQUEST);
        }
        ClubJoinEntry joinEntry = clubJoinEntryRepository.findByUserIdAndClubId(banRequest.getBanUserId(), clubId);
        if (joinEntry != null) {
            clubJoinEntryRepository.delete(joinEntry);
            log.info("user " + userId + " banned user " + banRequest.getBanUserId() + " from club " + clubId);
            //추방 후 가입 제한 추가시 여기에 logic
            Message message = new Message(String.format("user %d 가 클럽에서 강퇴되었습니다",banRequest.getBanUserId()));
            return ResponseEntity.ok(message);
        } else {
            Message message = new Message("클럽 가입 정보가 없습니다.");
            return ResponseEntity.ok(message);
        }
    }

    //클럽 생성
    public ClubDetailResponse createClub(ConfirmClubCreationDto creationRequest){
        Club club = new Club(creationRequest);
        clubRepository.saveAndFlush(club);
        List<String> clubImageUrlList = clubImageUrlRepository.findAllByClubId(creationRequest.getCreateClubId())
                .stream()
                .peek(image->image.setClubId(club.getId()))
                .map(ClubImageUrl::getImageUrl)
                .toList();
        chatService.makeChat(club.getId(), ChatTypeEnum.CLUB);
        return new ClubDetailResponse(club, clubImageUrlList); // querydsl에서 List로 projection이 가능한가 확인해봐야함
    }


    public ResponseEntity<List<Event>> getClubEventList(User user, Long clubId) {
        List<Event> eventList = eventService.getEventList(clubId);
        return ResponseEntity.ok(eventList);
    }

    public ResponseEntity<Message> deleteClub(User user, Long clubId) {
        //임시구현, 로직 변경 필요할듯 (softdelete ? orphanremoval ?
        Club club = loadClubByClubId(clubId);
        if(!club.getOwnerId().equals(user.getId())){
            return new ResponseEntity<>(new Message("내 클럽이 아닙니다"), HttpStatus.UNAUTHORIZED);
        }
        else{
            club.flagDeleted(true);
            return ResponseEntity.ok(new Message("삭제되었습니다"));
        }
    }





    /////////////////////private method///////////////////////

    //클럽id Null 체크

    private Club loadClubByClubId(Long clubId) {
        Club club = clubRepository.findById(clubId).orElse(null);
        if(club == null){
            log.info("failed to find Club with id : " + clubId);
            throw new NullPointerException("해당 클럽을 찾을 수 없습니다");
        }
        else if(club.getIsDeleted().equals(true)){
            throw new NullPointerException("삭제된 클럽입니다");
        }
        return club;
    }

    public Integer userOwnedClubCount(Long userId) {
        return clubRepository.countByOwnerIdAndIsDeletedFalse(userId);
    }
}
