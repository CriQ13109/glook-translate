package app.glook.translate

import app.glook.translate.data.engine.RegisterDetector
import app.glook.translate.domain.model.Register
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterDetectorTest {
    @Test fun hasipsio() = assertEquals(Register.HASIPSIO, RegisterDetector.detect("회의는 내일 오전 10시에 시작합니다. 참석 여부를 알려 주십시오.", "ko"))
    @Test fun haeyo() = assertEquals(Register.HAEYO, RegisterDetector.detect("이거 어디서 사셨어요? 너무 예뻐요!", "ko"))
    @Test fun banmal() = assertEquals(Register.BANMAL, RegisterDetector.detect("야 오늘 저녁 뭐 먹을래? ㅋㅋ 배고파", "ko"))
    @Test fun business() = assertEquals(Register.BUSINESS, RegisterDetector.detect("첨부한 견적서 검토 부탁드립니다.", "ko"))
    @Test fun technical() = assertEquals(Register.TECHNICAL, RegisterDetector.detect("서버의 API 응답이 JSON 형식이 아닙니다.", "ko"))
    @Test fun japanesePolite() = assertEquals(Register.HAEYO, RegisterDetector.detect("駅はどこですか。", "ja"))
    @Test fun englishBusiness() = assertEquals(Register.BUSINESS, RegisterDetector.detect("Dear Mr. Kim, please find attached the invoice.", "en"))
    @Test fun empty() = assertEquals(Register.NEUTRAL, RegisterDetector.detect("   ", "ko"))
}
