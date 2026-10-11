import React, { useState } from 'react';
import { supabase } from '../supabase';
import { KeyRound, ShieldAlert, Sparkles } from 'lucide-react';

interface LoginModalProps {
  onLoginSuccess: (code: string, name: string) => void;
}

export const LoginModal: React.FC<LoginModalProps> = ({ onLoginSuccess }) => {
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanCode = code.trim().toUpperCase();
    if (!cleanCode) {
      setError('Please enter your 6-character access code.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const { data, error: sbError } = await supabase
        .from('access_codes')
        .select('*')
        .eq('code', cleanCode)
        .limit(1);

      if (sbError) throw sbError;

      if (!data || data.length === 0) {
        setError(`Access code '${cleanCode}' not found. Please verify your phone's code.`);
        setLoading(false);
        return;
      }

      const userName = data[0].name || 'User';
      localStorage.setItem('kairo_user_code', cleanCode);
      localStorage.setItem('kairo_user_name', userName);
      onLoginSuccess(cleanCode, userName);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`Login failed: ${msg}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
      <div className="w-full max-w-md rounded-3xl bg-[#1e1f26] border border-[#474556]/60 p-7 shadow-2xl animate-fade-in text-center">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-[#5a45ff]/20 text-[#c5c0ff]">
          <KeyRound className="h-7 w-7" />
        </div>

        <h2 className="text-2xl font-bold tracking-tight text-[#e3e1ec]">Connect to KAIRO</h2>
        <p className="mt-1 text-sm text-[#c8c4d9]">
          Enter your 6-character Workspace Access Code from your Android phone app.
        </p>

        {error && (
          <div className="mt-4 flex items-center gap-2 rounded-xl bg-[#93000a]/40 border border-[#ffb4ab]/30 p-3 text-left text-xs text-[#ffdad6]">
            <ShieldAlert className="h-4 w-4 shrink-0 text-[#ffb4ab]" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <input
              type="text"
              maxLength={12}
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              placeholder="ENTER 6-DIGIT CODE"
              className="w-full text-center text-2xl font-mono uppercase tracking-widest rounded-2xl bg-[#12131a] border border-[#474556] py-3.5 px-4 text-[#e3e1ec] focus:outline-none focus:border-[#c5c0ff] focus:ring-2 focus:ring-[#5a45ff]/40 transition-all placeholder:text-[#474556]"
              autoFocus
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 rounded-2xl bg-[#5a45ff] hover:bg-[#6853ff] active:scale-[0.98] py-3.5 px-4 font-semibold text-white shadow-lg shadow-[#5a45ff]/30 transition-all disabled:opacity-50 cursor-pointer"
          >
            {loading ? (
              <span className="flex items-center gap-2">Connecting...</span>
            ) : (
              <>
                <Sparkles className="h-4 w-4" />
                <span>Open Workspace</span>
              </>
            )}
          </button>
        </form>

        <p className="mt-5 text-xs text-[#918ea2]">
          Your tasks and alarms will sync live with your phone in real-time.
        </p>
      </div>
    </div>
  );
};
