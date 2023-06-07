package com.example.moyiza_be.user.controller;

import com.example.moyiza_be.common.security.userDetails.UserDetailsImpl;
import com.example.moyiza_be.user.dto.*;
import com.example.moyiza_be.user.email.EmailRequestDto;
import com.example.moyiza_be.user.email.EmailService;
import com.example.moyiza_be.user.service.UserService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final EmailService emailService;

    //회원가입
    @PostMapping ("/signup")
    public ResponseEntity<?> signup(@RequestPart(value = "data") SignupRequestDto requestDto,
                                    @RequestPart(value = "imageFile")@Nullable MultipartFile image){
        return userService.signup(requestDto, image);
    }

    //이메일 인증 - 이메일 전송
    @PostMapping("/signup/confirmEmail")
    public ResponseEntity<?> confirmEmail(@RequestBody EmailRequestDto requestDto) throws Exception {
        return emailService.sendSimpleMessage(requestDto);
    }

    @PostMapping("/signup/verifyCode")
    public ResponseEntity<?> verifyCode(@RequestBody String code) throws ChangeSetPersister.NotFoundException {
        return emailService.verifyCode(code);
    }

    //로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto requestDto, HttpServletResponse response){
        return userService.login(requestDto, response);
    }
    //로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response, @AuthenticationPrincipal UserDetailsImpl userDetails){
        return userService.logout(request, response, userDetails.getUser().getEmail());
    }
    //회원정보 수정
    @PutMapping(value = "/profile",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE,MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> updateProfile(@RequestPart(value = "imageFile") MultipartFile image,
                                           @RequestPart(value = "data") UpdateRequestDto requestDto,
                                           @AuthenticationPrincipal UserDetailsImpl userDetails){
        return userService.updateProfile(image, requestDto, userDetails.getUser().getEmail());
    }

    //Refresh 토큰으로 Access 토큰 재발급
    @GetMapping("/reissue")
    public ResponseEntity<?> reissueToken(@CookieValue(value = "REFRESH_TOKEN", required = false) String refreshToken, HttpServletResponse response){
        return userService.reissueToken(refreshToken, response);
    }

    //이메일 중복 확인
    @PostMapping("/check/email")
    public ResponseEntity<?> isDuplicatedEmail(@RequestBody CheckEmailRequestDto requestDto){
        return userService.isDuplicatedEmail(requestDto);
    }

    //닉네임 중복 확인
    @PostMapping("/check/nickname")
    public ResponseEntity<?> isDuplicatedNick(@RequestBody CheckNickRequestDto requestDto){
        return userService.isDuplicatedNick(requestDto);
    }
}
