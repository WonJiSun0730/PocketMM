import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import "./AuthPage.css"

export default function LoginPage({ onAuthenticated, onOpenSignup }) {
  const [form, setForm] = useState({ loginId: "", password: "" })
  const [error, setError] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [options, setOptions] = useState(null)
  const [verification, setVerification] = useState(false)
  const [code, setCode] = useState("")
  const [resendAt, setResendAt] = useState(0)
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    fetch("/api/auth/options", { credentials: "include" })
      .then((response) => { if (!response.ok) throw new Error(); return response.json() })
      .then(setOptions).catch(() => setError("로그인 설정을 불러오지 못했습니다. 새로고침해주세요."))
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  async function verify(event) {
    event.preventDefault()
    setError(""); setSubmitting(true)
    try {
      const response = await fetch("/api/auth/verify-login", {
        method: "POST", credentials: "include",
        headers: { "Content-Type": "application/json" }, body: JSON.stringify({ code }),
      })
      if (!response.ok) throw new Error("인증번호가 올바르지 않거나 만료되었습니다. 5회 틀리면 다시 발송해주세요.")
      await onAuthenticated()
    } catch (e) { setError(e.message) }
    finally { setSubmitting(false) }
  }

  async function submit(event) {
    event.preventDefault()
    setError("")
    setSubmitting(true)

    try {
      const body = new URLSearchParams(form)
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
        credentials: "include",
      })

      if (!response.ok) {
        if (response.status === 429) throw new Error("인증번호 재발송은 2분 후에 가능합니다.")
        if (response.status === 503) throw new Error("인증번호 발송에 실패했습니다. 메일 설정을 확인해주세요.")
        throw new Error("아이디 또는 비밀번호를 확인해주세요.")
      }

      if (response.status === 202) {
        setVerification(true); setCode(""); setResendAt(Date.now() + 120000)
        return
      }

      await onAuthenticated()
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthFrame title="다시 만나요" description="PocketMM의 작은 세계로 돌아오세요.">
      {verification ? <form className="auth-form" onSubmit={verify}>
        <p>{options?.maskedEmail ?? "지정된 이메일"}로 인증번호를 보냈습니다. 5분 안에 입력해주세요.</p>
        <AuthField label="인증번호">
          <input required inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
            value={code} onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, ""))} />
        </AuthField>
        {error && <p className="auth-error">{error}</p>}
        <Button type="submit" disabled={submitting}>{submitting ? "확인 중..." : "인증하고 로그인"}</Button>
        <Button type="button" disabled={submitting || now < resendAt} onClick={submit}>
          {now < resendAt ? `${Math.ceil((resendAt - now) / 1000)}초 후 재발송` : "인증번호 재발송"}
        </Button>
        <button type="button" disabled={submitting} onClick={() => { setVerification(false); setCode(""); setError("") }}>로그인 화면으로</button>
      </form> : <form className="auth-form" onSubmit={submit}>
        <AuthField label="아이디">
          <input required autoComplete="username" value={form.loginId}
            onChange={(e) => setForm({ ...form, loginId: e.target.value })} />
        </AuthField>
        <AuthField label="비밀번호">
          <input required type="password" autoComplete="current-password" value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </AuthField>
        {error && <p className="auth-error">{error}</p>}
        <Button className="w-full" disabled={submitting || !options} type="submit">
          {submitting ? "로그인 중..." : "로그인"}
        </Button>
      </form>}
      {options?.registrationEnabled && <p className="auth-switch">처음 오셨나요? <button type="button" onClick={onOpenSignup}>회원가입</button></p>}
    </AuthFrame>
  )
}

export function AuthFrame({ title, description, children }) {
  return <main className="auth-shell"><section className="auth-card">
    <header className="auth-brand"><div className="auth-logo-placeholder" /><span>PocketMM</span></header>
    <div className="auth-heading"><h1>{title}</h1><p>{description}</p></div>
    {children}
  </section></main>
}

export function AuthField({ label, children, hint }) {
  return <label className="auth-field"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>
}
