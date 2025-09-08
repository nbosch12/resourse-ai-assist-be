package com.bosch.user.resourceaiassist.controller;

import com.bosch.user.resourceaiassist.servicesimpl.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
class RagController {
    private final RagService rag;

    @GetMapping("/api/ask")
    public Map<String,Object> ask(@RequestParam("q") String q) {
        var ans = rag.ask(q);
        return Map.of(
                "question", q,
                "answer", ans.answer(),
                "citations", ans.citations().stream().map(h -> Map.of(
                        "chunkId", h.chunkId(),
                        "docId",   h.docId(),
                        "pageStart", h.pageStart(),
                        "pageEnd",   h.pageEnd(),
                        "score",     h.score()
                )).toList()
        );
    }
}
