package com.example.projectmm.domain.dialogue;

import java.util.List;
import java.util.regex.Pattern;

/** 명백한 한국어 사용자 주체 단정만 검사한다. 일반적인 의미 판별기를 대신하지 않는다. */
public final class DialogueAgencyPolicy {
    private DialogueAgencyPolicy() {}

    private static final Pattern NON_ACTUAL = Pattern.compile(
            "[?？]|(?i:OOC)|만약|가정|계획|예정|고민|생각|속으로|않|못|(?:^|\\s)안\\s|싶|겠|려[고던]|듯|것 같|보였|보인다|라고|다고|다면|거짓");
    private static final List<Pattern> CONSEQUENCES = List.of(
            Pattern.compile("문을\\s*열(?:었다|었어|고)"),
            Pattern.compile("방(?:에|으로)\\s*들어(?:갔다|갔어|섰다)"),
            Pattern.compile("(?:도망쳤|도망갔|달아났)(?:다|어)"),
            Pattern.compile("(?:결심|결정|승낙|동의|고백)(?:했다|했어)"),
            Pattern.compile("(?:입맞췄|키스했|공격했)(?:다|어)"),
            Pattern.compile("(?:두려웠|행복했|슬펐|분노했|사랑에 빠졌)(?:다|어)"),
            Pattern.compile("(?:공포|두려움|행복|분노|슬픔)(?:을|를)\\s*느꼈(?:다|어)"),
            Pattern.compile("(?:공격|탈출|설득|구출)(?:에|을|를)\\s*(?:성공|실패)(?:했다|했어)"),
            Pattern.compile("(?:기절했|쓰러졌|죽었)(?:다|어)"));

    public static DialogueTurn enforce(DialogueTurn turn, String userText, DialogueContext context) {
        if (!violates(turn.reply(), userText, context)) return turn;
        // 거절된 본문의 근거로 NPC/플레이어/Custom State를 바꾸지 않는다. 재호출과 메타데이터 노출도 없다.
        return new DialogueTurn("*다음 순간은 아직 정해지지 않았다.*", DialogueStateChange.none(),
                DialoguePlayerStateChange.none(), List.of());
    }

    public static boolean violates(String reply, String userText, DialogueContext context) {
        if (reply == null) return false;
        String name = context.playerProfile() == null ? null : context.playerProfile().name();
        String subjects = "당신(?:은|이)|너(?:는|가)";
        if (name != null && !name.isBlank() && !name.equals(context.characterName()))
            subjects += "|" + Pattern.quote(name) + "(?:은|는|이|가)";
        Pattern subject = Pattern.compile("(?:^|\\s)(?:" + subjects + ")\\s+");
        // RP 계약의 *지문*만 검사한다. 따옴표 없는 NPC 대사를 사용자 사실의 단정으로 오인하지 않는다.
        var prose = Pattern.compile("\\*([^*]+)\\*", Pattern.DOTALL).matcher(reply);
        StringBuilder narration = new StringBuilder();
        while (prose.find()) narration.append(withoutQuotes(prose.group(1))).append('\n');
        for (String sentence : sentences(narration.toString())) {
            var actor = subject.matcher(sentence.replace('*', ' ').strip());
            if (!actor.find()) continue;
            String claim = sentence.replace('*', ' ').strip().substring(actor.end());
            if (hasOtherSubject(claim, context.characterName())) continue;
            // 생각을 했다는 단정도 사용자 내면의 생성이다. 계획/가정 제외 규칙보다 먼저 확인한다.
            if (Pattern.compile("(?:라고|다고)\\s*생각했다|확신했다|깨달았다").matcher(claim).find()
                    && (userText == null || !withoutQuotes(userText).contains(claim))) return true;
            if (NON_ACTUAL.matcher(claim).find()) continue;
            for (Pattern consequence : CONSEQUENCES) {
                var asserted = consequence.matcher(claim);
                while (asserted.find()) {
                    // 같은 분류의 다른 결과(실패→성공, 슬픔→행복)가 근거로 통과하지 않도록 실제 단정을 대조한다.
                    String action = asserted.group();
                    Pattern supported = action.startsWith("문을") ? consequence
                            : Pattern.compile(Pattern.quote(action.replaceFirst("[다어]$", "")) + "[다어]");
                    if (!userEstablished(userText, supported, context.characterName())) return true;
                }
            }
        }
        return false;
    }

    private static boolean userEstablished(String input, Pattern consequence, String npcName) {
        if (input == null) return false;
        for (String sentence : sentences(withoutQuotes(input))) {
            if (!NON_ACTUAL.matcher(sentence).find() && !hasOtherSubject(sentence, npcName)
                    && consequence.matcher(sentence).find()) return true;
        }
        return false;
    }

    public static boolean supportsStateEvidence(String input, String evidence) {
        if (input == null || evidence == null || evidence.isBlank()) return false;
        // 동일 부분 문자열이어도 계획/부정/질문에서 잘라낸 근거는 완료된 상태 변화로 사용하지 않는다.
        for (String sentence : sentences(input.replace("*", ""))) {
            if (sentence.contains(evidence.replace("*", "")) && !NON_ACTUAL.matcher(sentence).find()) return true;
        }
        return false;
    }

    private static boolean hasOtherSubject(String text, String npcName) {
        if (Pattern.compile("(?:^|\\s)(?:그는|그가|그녀는|그녀가|NPC는|NPC가)\\s").matcher(text).find()) return true;
        return npcName != null && !npcName.isBlank()
                && Pattern.compile(Pattern.quote(npcName) + "(?:은|는|이|가)\\s").matcher(text).find();
    }

    private static String withoutQuotes(String text) {
        return text.replaceAll("\"[^\"]*\"|“[^”]*”|'[^']*'|‘[^’]*’|「[^」]*」", " ");
    }

    private static String[] sentences(String text) {
        return text.split("(?<=[.!?。！？\\n])");
    }
}
