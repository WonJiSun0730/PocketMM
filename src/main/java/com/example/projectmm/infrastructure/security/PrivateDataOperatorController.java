package com.example.projectmm.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/private-data")
public class PrivateDataOperatorController {
    private final PrivateDataOperatorService service;
    public PrivateDataOperatorController(PrivateDataOperatorService service) { this.service=service; }
    @PostMapping("/search")
    public List<PrivateDataOperatorService.Metadata> search(@RequestBody PrivateDataOperatorService.Request request,
            Authentication auth, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return service.search(auth,request);
    }
    @PostMapping("/decrypt")
    public List<PrivateDataOperatorService.Disclosure> decrypt(@RequestBody PrivateDataOperatorService.Request request,
            Authentication auth, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return service.decrypt(auth,request);
    }
}
