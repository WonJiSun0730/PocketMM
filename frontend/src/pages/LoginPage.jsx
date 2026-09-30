import { useState } from "react"
import { Button } from "@/components/ui/button"
import "./AuthPage.css"

export default function LoginPage({ onAuthenticated, onOpenSignup }) {
  const [form, setForm] = useState({ loginId: "", password: "" })
  const [error, setError] = useState("")
  const [submitting, setSubmitting] = useState(false)

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
        throw new Error("아이디 또는 비밀번호를 확인해주세요.")
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
      <form className="auth-form" onSubmit={submit}>
        <AuthField label="아이디">
          <input required autoComplete="username" value={form.loginId}
            onChange={(e) => setForm({ ...form, loginId: e.target.value })} />
        </AuthField>
        <AuthField label="비밀번호">
          <input required type="password" autoComplete="current-password" value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </AuthField>
        {error && <p className="auth-error">{error}</p>}
        <Button className="w-full" disabled={submitting} type="submit">
          {submitting ? "로그인 중..." : "로그인"}
        </Button>
      </form>
      <p className="auth-switch">처음 오셨나요? <button type="button" onClick={onOpenSignup}>회원가입</button></p>
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
