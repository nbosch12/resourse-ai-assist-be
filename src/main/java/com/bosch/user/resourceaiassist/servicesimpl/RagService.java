package com.bosch.user.resourceaiassist.servicesimpl;


import com.bosch.user.resourceaiassist.repo.SemanticSearchRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagService {
    private final AiCoreClient ai;
    private final SemanticSearchRepo repo;



    public record RagAnswer(String answer, List<SemanticSearchRepo.Hit> citations) {}

    public RagAnswer ask(String userPrompt) {
        // 1) embed query
        float[] qv = ai.embed(userPrompt);

        // 2) ANN search (latest versions only)
        List<SemanticSearchRepo.Hit> hits = repo.topKByQueryVector(qv, 12);

        // 3) Build context block (trim to configured limit)
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            var h = hits.get(i);
            ctx.append("\n[Chunk #").append(i + 1)
                    .append(" | DOC ").append(h.docId())
                    .append(" | pages ").append(nz(h.pageStart())).append("-").append(nz(h.pageEnd()))
                    .append("]\n")
                    .append(h.text()).append("\n");
            if (ctx.length() >= 12000) break;
        }

        // 4) System + user messages (RAG)
        List<AiCoreClient.ChatMessage> msgs = List.of(
                new AiCoreClient.ChatMessage("system",
                        "You are a helpful assistant. Use ONLY the provided context. " +
                                "If the answer isn't in the context, say you don't know."),
                new AiCoreClient.ChatMessage("user",
                        "User question:\n" + userPrompt +
                                "\n\nContext (excerpts):\n```" + ctx + "```" +
                                "\n\nAnswer clearly. Cite chunks by their [Chunk #].")
        );

        String answer = ai.chat(msgs, 1024, 0.2);
        return new RagAnswer(answer, hits);
    }

    private static String nz(Integer x){ return x == null ? "?" : x.toString(); }
}
