import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { authApi } from "@/api/endpoints";
import { apiError } from "@/api/client";
import { useAuthStore } from "@/store/authStore";
import { homeFor } from "@/components/common/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useToast } from "@/components/ui/toast";
import { AuthShell } from "./AuthShell";
import { RecaptchaVerifier, signInWithPhoneNumber } from "firebase/auth";
import { firebaseAuth } from "@/lib/firebase";

const schema = z.object({
  email: z.string().email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});

export function LoginPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState("email");
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(schema),
  });

  const finishLogin = (auth) => {
    setSession(auth);
    toast(`Welcome back, ${auth.user.name.split(" ")[0]}!`, "success");
    navigate(homeFor(auth.user.role));
  };

  const submit = async (data) => {
    setLoading(true);
    try {
      finishLogin(await authApi.login(data));
    } catch (e) {
      toast(apiError(e), "error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell>
      <div className="mb-6">
        <h2 className="text-2xl font-bold">Sign in</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Welcome back. Enter your details.
        </p>
      </div>

      {/* Email / Phone toggle */}
      <div className="mb-5 grid grid-cols-2 gap-1 rounded-lg border bg-muted/40 p-1">
        {["email", "phone"].map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setMode(m)}
            className={
              "rounded-md py-1.5 text-sm font-medium transition-colors " +
              (mode === m
                ? "bg-card text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground")
            }
          >
            {m === "email" ? "Email" : "Phone (OTP)"}
          </button>
        ))}
      </div>

      {mode === "phone" ? (
        <PhoneLogin onSuccess={finishLogin} />
      ) : (
        <form onSubmit={handleSubmit(submit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              placeholder="you@example.com"
              {...register("email")}
            />
            {errors.email && (
              <p className="text-xs text-destructive">{errors.email.message}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              placeholder="••••••••"
              {...register("password")}
            />
            {errors.password && (
              <p className="text-xs text-destructive">
                {errors.password.message}
              </p>
            )}
          </div>
          <Button type="submit" className="w-full" loading={loading}>
            Sign in
          </Button>
        </form>
      )}

      <p className="mt-8 text-center text-sm text-muted-foreground">
        Don't have an account?{" "}
        <Link
          to="/register"
          className="font-medium text-primary hover:underline"
        >
          Sign up
        </Link>
      </p>
    </AuthShell>
  );
}

// Normalise to E.164 for Firebase: bare 10-digit → +91, otherwise honour a typed +cc.
function toE164(raw) {
  const trimmed = raw.replace(/[\s-]/g, "");
  if (/^\+[0-9]{8,15}$/.test(trimmed)) return trimmed;
  if (/^[0-9]{10}$/.test(trimmed)) return "+91" + trimmed;
  if (/^[0-9]{11,15}$/.test(trimmed)) return "+" + trimmed;
  return null;
}

function PhoneLogin({ onSuccess }) {
  const { toast } = useToast();
  const [step, setStep] = useState("phone");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const verifierRef = useRef(null);
  const confirmationRef = useRef(null);
  const recaptchaHostRef = useRef(null);

  // Render reCAPTCHA into a BRAND-NEW element each attempt — reusing one trips
  // grecaptcha's "already been rendered in this element" error on retries.
  const freshVerifier = () => {
    try {
      verifierRef.current?.clear();
    } catch {
      /* ignore */
    }
    const host = recaptchaHostRef.current;
    if (!host) throw new Error("reCAPTCHA host not ready");
    host.innerHTML = "";
    const el = document.createElement("div");
    host.appendChild(el);
    verifierRef.current = new RecaptchaVerifier(firebaseAuth, el, {
      size: "invisible",
    });
    return verifierRef.current;
  };

  const sendCode = async () => {
    const e164 = toE164(phone);
    if (!e164) {
      toast("Enter a valid phone number (e.g. +91 98765 43210)", "error");
      return;
    }
    setLoading(true);
    try {
      confirmationRef.current = await signInWithPhoneNumber(
        firebaseAuth,
        e164,
        freshVerifier(),
      );
      setStep("code");
      toast(`Code sent to ${e164}`, "success");
    } catch (e) {
      toast(firebaseError(e), "error");
    } finally {
      setLoading(false);
    }
  };

  const verify = async () => {
    if (!confirmationRef.current) return;
    setLoading(true);
    try {
      const cred = await confirmationRef.current.confirm(code.trim());
      const idToken = await cred.user.getIdToken();
      onSuccess(await authApi.firebaseLogin(idToken));
    } catch (e) {
      toast(firebaseError(e), "error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      {step === "phone" ? (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="phone">Phone number</Label>
            <Input
              id="phone"
              type="tel"
              inputMode="tel"
              placeholder="+91 98765 43210"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && sendCode()}
            />
          </div>
          <Button className="w-full" loading={loading} onClick={sendCode}>
            Send code
          </Button>
          <p className="text-center text-xs text-muted-foreground">
            Firebase will text you a real verification code.
          </p>
        </>
      ) : (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="code">Enter the code sent to {toE164(phone)}</Label>
            <Input
              id="code"
              inputMode="numeric"
              placeholder="6-digit code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && verify()}
            />
          </div>
          <Button className="w-full" loading={loading} onClick={verify}>
            Verify &amp; sign in
          </Button>
          <button
            type="button"
            className="w-full text-center text-xs text-muted-foreground hover:text-foreground"
            onClick={() => {
              setStep("phone");
              setCode("");
              confirmationRef.current = null;
            }}
          >
            ← Change number
          </button>
        </>
      )}
      {/* Invisible reCAPTCHA mounts a fresh child element here on each attempt. */}
      <div ref={recaptchaHostRef} />
    </div>
  );
}

function firebaseError(e) {
  const code = e?.code ?? "";
  if (code === "auth/invalid-phone-number")
    return "That phone number looks invalid";
  if (code === "auth/too-many-requests")
    return "Too many attempts — try again later";
  if (code === "auth/invalid-verification-code")
    return "Incorrect code, please re-check";
  if (code === "auth/code-expired") return "Code expired — request a new one";
  if (code === "auth/quota-exceeded")
    return "Daily SMS limit reached (Firebase free tier: 10/day)";
  if (code === "auth/operation-not-allowed") {
    return "SMS to this region is disabled in Firebase — enable India under Authentication → Settings → SMS region policy";
  }
  return e?.message ?? "Something went wrong";
}
