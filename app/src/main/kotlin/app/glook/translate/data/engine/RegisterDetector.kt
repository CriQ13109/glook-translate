package app.glook.translate.data.engine

import app.glook.translate.domain.model.Register

/**
 * 온디바이스 경량 화법 판별기 (규칙 기반). LLM 프롬프트에 "힌트"로만 전달됩니다.
 * ML Kit 온디바이스 번역은 화법 제어 기능이 없으므로, 오프라인 폴백 결과에는 화법 매칭이 적용되지 않습니다.
 */
object RegisterDetector {

    private val hasipsio = Regex("""(습니다|ㅂ니다|습니까|ㅂ니까|십시오|십시요|읍시다)[.!?…\s]*$""")
    private val haeyo = Regex("""(요|죠|세요|에요|예요|해요|네요|군요|래요|까요)[.!?~…\s]*$""")
    private val banmal = Regex("""(야|냐|니|지|자|해|어|아|거든|잖아|는다|ㄴ다|다|래|네|군|걸|게|라)[.!?~…\s]*$""")
    private val laughter = Regex("""[ㅋㅎㅠㅜ]{2,}""")
    private val businessKo = Regex("""귀사|회신|검토 부탁|첨부|요청드립니다|부탁드립니다|협조|안내드립니다|송부|견적|계약|담당자""")
    private val businessEn = Regex("""(?i)\b(dear (mr|ms|mrs|sir|madam)|sincerely|best regards|kind regards|please find attached|per our|invoice|quotation|at your earliest convenience)\b""")
    private val casualEn = Regex("""(?i)\b(lol|lmao|gonna|wanna|gotta|ya|u|ur|dude|bro|omg|tbh|idk)\b""")
    private val jaPolite = Regex("""(です|ます|でした|ました|ません|ください|ございます|でしょう)(か|ね|よ)?[。！？!?\s]*$""")
    private val jaCasual = Regex("""(だよ|だね|じゃん|よね|だろ|だ|よ|ね|な|わ)[。！？!?\s]*$""")
    private val technical = Regex("""(?i)\b(api|sdk|http|json|sql|cpu|gpu|kernel|thread|mg|ml|dna|protocol|algorithm|regex)\b|함수|변수|서버|프로토콜|알고리즘|진단|투여|판례|조항""")

    fun detect(text: String, sourceCode: String): Register {
        val t = text.trim()
        if (t.isEmpty()) return Register.NEUTRAL
        val sentences = t.split(Regex("""(?<=[.!?。！？\n])""")).map { it.trim() }.filter { it.isNotEmpty() }

        if (technical.findAll(t).count() >= 2) return Register.TECHNICAL

        return when (sourceCode) {
            "ko" -> {
                if (businessKo.containsMatchIn(t)) return Register.BUSINESS
                var hs = 0; var hy = 0; var bm = 0
                for (s in sentences) when {
                    hasipsio.containsMatchIn(s) -> hs++
                    haeyo.containsMatchIn(s) -> hy++
                    banmal.containsMatchIn(s) -> bm++
                }
                if (laughter.containsMatchIn(t)) bm++
                when {
                    hs == 0 && hy == 0 && bm == 0 -> Register.NEUTRAL
                    hs >= hy && hs >= bm -> Register.HASIPSIO
                    hy >= bm -> Register.HAEYO
                    else -> Register.BANMAL
                }
            }
            "ja" -> {
                val polite = sentences.count { jaPolite.containsMatchIn(it) }
                val casual = sentences.count { jaCasual.containsMatchIn(it) && !jaPolite.containsMatchIn(it) }
                when {
                    polite == 0 && casual == 0 -> Register.NEUTRAL
                    polite >= casual -> Register.HAEYO
                    else -> Register.BANMAL
                }
            }
            "en" -> when {
                businessEn.containsMatchIn(t) -> Register.BUSINESS
                casualEn.containsMatchIn(t) || t.contains("!!") -> Register.BANMAL
                else -> Register.NEUTRAL
            }
            else -> Register.NEUTRAL
        }
    }
}
