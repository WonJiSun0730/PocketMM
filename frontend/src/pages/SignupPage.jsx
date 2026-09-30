import { useState } from "react"
import { Button } from "@/components/ui/button"
import { AuthField, AuthFrame } from "./LoginPage"
import "./AuthPage.css"

const initial = { loginId: "", password: "", passwordConfirm: "", name: "", email: "" }

export default function SignupPage({ onOpenLogin }) {
  const [form, setForm] = useState(initial)
  const [error, setError] = useState("")
  const [submitting, setSubmitting] = useState(false)

  function update(key, value) { setForm((current) => ({ ...current, [key]: value })) }

  async function submit(event) {
    event.preventDefault()
    setError("")
    if (form.password !== form.passwordConfirm) {
      setError("비밀번호와 비밀번호 확인이 일치하지 않습니다.")
      return
    }
    setSubmitting(true)
    try {
      const response = await fetch("/api/auth/signup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(form),
      })
      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || "회원가입에 실패했어요.")
      }
      onOpenLogin()
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthFrame title="작은 세계를 시작해요" description="계정을 만들고 나만의 캐릭터와 이야기를 이어가세요.">
      <form className="auth-form" onSubmit={submit}>
        <AuthField label="아이디" hint="영문 소문자와 숫자 5~20자">
          <input required minLength={5} maxLength={20} pattern="[a-z0-9]+" autoComplete="username"
            value={form.loginId} onChange={(e) => update("loginId", e.target.value)} />
        </AuthField>
        <AuthField label="이름">
          <input required maxLength={50} autoComplete="name"
            value={form.name} onChange={(e) => update("name", e.target.value)} />
        </AuthField>
        <AuthField label="이메일">
          <input required type="email" autoComplete="email"
            value={form.email} onChange={(e) => update("email", e.target.value)} />
        </AuthField>
        <div className="auth-two-column">
          <AuthField label="비밀번호" hint="8~64자">
            <input required minLength={8} maxLength={64} type="password" autoComplete="new-password"
              value={form.password} onChange={(e) => update("password", e.target.value)} />
          </AuthField>
          <AuthField label="비밀번호 확인">
            <input required type="password" autoComplete="new-password"
              value={form.passwordConfirm} onChange={(e) => update("passwordConfirm", e.target.value)} />
          </AuthField>
        </div>
        {error && <p className="auth-error">{error}</p>}
        <Button className="w-full" disabled={submitting} type="submit">
          {submitting ? "계정 만드는 중..." : "회원가입"}
        </Button>
      </form>
      <p className="auth-switch">이미 계정이 있나요? <button type="button" onClick={onOpenLogin}>로그인</button></p>
    </AuthFrame>
  )
}
