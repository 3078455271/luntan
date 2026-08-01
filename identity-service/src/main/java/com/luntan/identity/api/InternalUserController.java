package com.luntan.identity.api;

import com.luntan.identity.api.dto.IdentityDtos;
import com.luntan.identity.application.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;
    private final byte[] internalToken;

    public InternalUserController(UserService userService,
                                  @Value("${app.security.internal-token}") String internalToken) {
        this.userService = userService;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/summaries")
    public List<IdentityDtos.UserSummary> summaries(
            @RequestHeader("X-Internal-Token") String suppliedToken,
            @RequestBody IdentityDtos.UserSummaryRequest request) {
        if (!MessageDigest.isEqual(internalToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal service token");
        }
        return userService.findSummaries(request.userIds());
    }
}