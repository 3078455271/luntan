package com.luntan.forum.api;

import com.luntan.forum.api.dto.ForumDtos;
import com.luntan.forum.application.ForumService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sections")
public class SectionController {

    private final ForumService forumService;

    public SectionController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping
    public List<ForumDtos.SectionView> listSections() {
        return forumService.listSections();
    }
}