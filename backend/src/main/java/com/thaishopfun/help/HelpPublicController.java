package com.thaishopfun.help;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.help.HelpDtos.FaqView;
import com.thaishopfun.help.HelpDtos.FeedbackRequest;
import com.thaishopfun.help.HelpDtos.FeedbackResult;
import com.thaishopfun.help.HelpDtos.GuideView;
import com.thaishopfun.help.HelpDtos.TopicDetailView;
import com.thaishopfun.help.HelpDtos.TopicView;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@PublicEndpoint("public help center")
@RestController
@RequestMapping("/api/help")
public class HelpPublicController {
    private final HelpService help;

    public HelpPublicController(HelpService help) {
        this.help = help;
    }

    @GetMapping("/topics")
    public List<TopicView> topics(@RequestParam(required = false) String lang) {
        return help.listTopics(lang);
    }

    @GetMapping("/topics/{slug}")
    public TopicDetailView topic(@PathVariable String slug, @RequestParam(required = false) String lang) {
        return help.topicDetail(slug, lang);
    }

    @GetMapping("/faqs/{id}")
    public FaqView faq(@PathVariable UUID id, @RequestParam(required = false) String lang) {
        return help.faqDetail(id, lang);
    }

    /** Anonymous callers identify themselves with sessionKey; a signed-in bearer token takes precedence. */
    @PostMapping("/faqs/{id}/feedback")
    public FeedbackResult feedback(@PathVariable UUID id, @RequestBody(required = false) FeedbackRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return help.submitFeedback(id, request, RequestUser.id(jwt));
    }

    @GetMapping("/faqs")
    public List<FaqView> faqs(@RequestParam(required = false) String lang,
                              @RequestParam(required = false) String topic,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) String audience) {
        return help.listFaqs(lang, topic, q, audience);
    }

    @GetMapping("/guides")
    public List<GuideView> guides(@RequestParam(required = false) String lang) {
        return help.listGuides(lang);
    }

    @GetMapping("/images/{mediaId}")
    public ResponseEntity<Resource> image(@PathVariable UUID mediaId) {
        return help.serveImage(mediaId);
    }
}
