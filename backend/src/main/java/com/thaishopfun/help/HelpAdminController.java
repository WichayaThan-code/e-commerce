package com.thaishopfun.help;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.help.HelpDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Platform admin management of help topics, FAQs and guides. */
@RestController
@RequestMapping("/api/admin/help")
public class HelpAdminController {
    private final HelpService help;

    public HelpAdminController(HelpService help) {
        this.help = help;
    }

    // ---- topics ----
    @RequirePermission("PLATFORM_HELP_READ")
    @GetMapping("/topics")
    public List<AdminTopic> topics() { return help.adminTopics(); }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminTopic createTopic(@RequestBody TopicRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.createTopic(request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PutMapping("/topics/reorder")
    public List<AdminTopic> reorderTopics(@RequestBody ReorderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.reorderTopics(request.ids(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PutMapping("/topics/{id}")
    public AdminTopic updateTopic(@PathVariable UUID id, @RequestBody TopicRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.updateTopic(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PatchMapping("/topics/{id}/active")
    public AdminTopic setTopicActive(@PathVariable UUID id, @RequestBody ActiveRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.setTopicActive(id, request.active(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        help.deleteTopic(id, RequestUser.id(jwt));
    }

    // ---- faqs ----
    @RequirePermission("PLATFORM_HELP_READ")
    @GetMapping("/faqs")
    public List<AdminFaq> faqs() { return help.adminFaqs(); }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PostMapping("/faqs")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminFaq createFaq(@RequestBody FaqRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.createFaq(request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PutMapping("/faqs/reorder")
    public List<AdminFaq> reorderFaqs(@RequestBody ReorderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.reorderFaqs(request.ids(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PutMapping("/faqs/{id}")
    public AdminFaq updateFaq(@PathVariable UUID id, @RequestBody FaqRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.updateFaq(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PatchMapping("/faqs/{id}/active")
    public AdminFaq setFaqActive(@PathVariable UUID id, @RequestBody ActiveRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.setFaqActive(id, request.active(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PatchMapping("/faqs/{id}/reviewed")
    public AdminFaq markFaqReviewed(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return help.markFaqReviewed(id, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @DeleteMapping("/faqs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFaq(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        help.deleteFaq(id, RequestUser.id(jwt));
    }

    // ---- guides ----
    @RequirePermission("PLATFORM_HELP_READ")
    @GetMapping("/guides")
    public List<AdminGuide> guides() { return help.adminGuides(); }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PostMapping("/guides")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminGuide createGuide(@RequestBody GuideRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.createGuide(request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PutMapping("/guides/reorder")
    public List<AdminGuide> reorderGuides(@RequestBody ReorderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.reorderGuides(request.ids(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PutMapping("/guides/{id}")
    public AdminGuide updateGuide(@PathVariable UUID id, @RequestBody GuideRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.updateGuide(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PatchMapping("/guides/{id}/active")
    public AdminGuide setGuideActive(@PathVariable UUID id, @RequestBody ActiveRequest request, @AuthenticationPrincipal Jwt jwt) {
        return help.setGuideActive(id, request.active(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_HELP_MANAGE")
    @DeleteMapping("/guides/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGuide(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        help.deleteGuide(id, RequestUser.id(jwt));
    }

    // ---- images ----
    @RequirePermission("PLATFORM_HELP_MANAGE")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HelpImageUpload uploadImage(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        return help.uploadImage(file, RequestUser.id(jwt));
    }
}
