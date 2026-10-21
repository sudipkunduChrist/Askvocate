import { useId, useRef, useState, type FormEvent } from 'react';
import { IS_DEMO_MODE, login } from '../api/client';
import { toErrorMessage } from '../api/errors';
import { Brand } from '../components/Brand';
import { Icon } from '../components/Icon';

interface LoginScreenProps {
  sessionMessage: string | null;
  onSignedIn: (token: string) => void;
}

export function LoginScreen({ sessionMessage, onSignedIn }: LoginScreenProps) {
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const passwordRef = useRef<HTMLInputElement>(null);
  const passwordId = useId();
  const hintId = useId();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!password.trim()) {
      setError('Enter the admin password to continue.');
      passwordRef.current?.focus();
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const token = await login(password);
      onSignedIn(token);
    } catch (signInError) {
      setError(toErrorMessage(signInError));
      passwordRef.current?.focus();
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-page__rule" aria-hidden="true" />
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-card__top">
          <Brand />
        </div>

        <div className="login-card__intro">
          <h1 id="login-title">Admin sign in</h1>
          <p>
            Sign in to review identity documents and lawyer profiles. Decisions
            are recorded against your admin session.
          </p>
        </div>

        {sessionMessage && (
          <div className="login-notice" role="status">
            <Icon name="clock" size={16} />
            <p>{sessionMessage}</p>
          </div>
        )}

        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <div className="field">
            <div className="field__label-row">
              <label htmlFor={passwordId}>Admin password</label>
              <span className="field__optional">Required</span>
            </div>
            <div className={`input-wrap${error ? ' input-wrap--error' : ''}`}>
              <span className="input-wrap__icon" aria-hidden="true">
                <Icon name="lock" size={16} />
              </span>
              <input
                ref={passwordRef}
                id={passwordId}
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value);
                  if (error) setError(null);
                }}
                autoComplete="current-password"
                aria-describedby={hintId}
                aria-invalid={error ? 'true' : undefined}
                autoFocus
                disabled={busy}
                className="input input--with-icon input--with-trailing"
              />
              <button
                type="button"
                className="input-wrap__toggle"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                aria-pressed={showPassword}
                tabIndex={0}
              >
                <Icon name={showPassword ? 'eyeOff' : 'eye'} size={16} />
              </button>
            </div>
            <p id={hintId} className="field__hint">
              Password-only access. There is no username.
            </p>
          </div>

          <div className="login-form__error-slot">
            {error && (
              <p className="form-error" role="alert">
                <Icon name="alert" size={15} />
                {error}
              </p>
            )}
          </div>

          <button type="submit" className="btn btn--primary btn--wide" disabled={busy}>
            {busy ? (
              <>
                <span className="spinner" aria-hidden="true" />
                Signing in…
              </>
            ) : (
              <>
                Sign in
                <Icon name="chevronRight" size={16} />
              </>
            )}
          </button>
        </form>

        {IS_DEMO_MODE && (
          <div className="login-demo">
            <span className="chip chip--amber chip--sm">
              <span className="chip__dot" aria-hidden="true" />
              Preview mode
            </span>
            <p>
              Sample data is loaded locally. Enter any password to explore the
              console; no backend is contacted.
            </p>
          </div>
        )}

        <footer className="login-card__footer">
          <Icon name="shield" size={14} />
          <p>Authorized personnel only. Sessions end when this tab closes.</p>
        </footer>
      </section>
    </main>
  );
}
