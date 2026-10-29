package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.BugReportService;
import com.example.projectmm.domain.chat.BugReport;
import com.example.projectmm.domain.chat.BugReportRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class BugReportController {
    private final BugReportService service;
    public BugReportController(BugReportService service) { this.service=service; }
    public record StatusRequest(BugReport.Status status) {}
    @PostMapping("/api/worlds/pockets/{sessionId}/bug-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public BugReportService.Receipt submit(@PathVariable Long sessionId,
            @RequestBody BugReportService.Submission request, Authentication auth, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return service.submit(sessionId,request,auth);
    }
    @GetMapping("/api/admin/bug-reports")
    public List<BugReportRepository.Metadata> inbox(@RequestParam(required=false) Long before,
            @RequestParam(required=false) BugReport.Status status, Authentication auth, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return service.inbox(auth,before,status);
    }
    @GetMapping("/api/admin/bug-reports/{id}")
    public BugReportService.Detail detail(@PathVariable Long id,Authentication auth,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return service.detail(auth,id);
    }
    @PutMapping("/api/admin/bug-reports/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable Long id,@RequestBody StatusRequest request,Authentication auth) {
        service.updateStatus(auth,id,request==null?null:request.status());
    }
}
