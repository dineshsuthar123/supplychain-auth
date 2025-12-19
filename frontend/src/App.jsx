import React, { useState, useEffect, useCallback, useMemo, createContext, useContext } from 'react';
import axios from 'axios';

// ============================================
// CONFIGURATION
// ============================================
const API_BASE = process.env.REACT_APP_API_BASE || '';
const PRODUCT_API_URL = process.env.REACT_APP_API_BASE_URL || `${API_BASE}/api/products`;
const VERIFICATION_API_URL = process.env.REACT_APP_VERIFICATION_API_URL || `${API_BASE}/api/verify`;
const AUTH_API_URL = `${API_BASE}/auth`;

// Configure axios defaults
axios.defaults.withCredentials = true;

// ============================================
// AUTH CONTEXT
// ============================================
const AuthContext = createContext(null);

const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth must be used within AuthProvider');
    return context;
};

const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [accessToken, setAccessToken] = useState(null);

    useEffect(() => {
        const savedUser = localStorage.getItem('user');
        const savedToken = localStorage.getItem('accessToken');
        if (savedUser && savedToken) {
            setUser(JSON.parse(savedUser));
            setAccessToken(savedToken);
        }
        setLoading(false);
    }, []);

    const login = async (emailOrUsername, password) => {
        const res = await axios.post(`${AUTH_API_URL}/login`, { emailOrUsername, password });
        setUser(res.data.user);
        setAccessToken(res.data.accessToken);
        localStorage.setItem('user', JSON.stringify(res.data.user));
        localStorage.setItem('accessToken', res.data.accessToken);
        return res.data;
    };

    const register = async (userData) => {
        const res = await axios.post(`${AUTH_API_URL}/register`, userData);
        setUser(res.data.user);
        setAccessToken(res.data.accessToken);
        localStorage.setItem('user', JSON.stringify(res.data.user));
        localStorage.setItem('accessToken', res.data.accessToken);
        return res.data;
    };

    const logout = async () => {
        try {
            await axios.post(`${AUTH_API_URL}/logout`, {}, {
                headers: { Authorization: `Bearer ${accessToken}` }
            });
        } catch (e) { }
        setUser(null);
        setAccessToken(null);
        localStorage.removeItem('user');
        localStorage.removeItem('accessToken');
    };

    const value = useMemo(() => ({
        user, loading, accessToken, login, register, logout, isAuthenticated: !!user
    }), [user, loading, accessToken]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

// ============================================
// THEME CONFIGURATION
// ============================================
const createTheme = (isDark) => ({
    mode: isDark ? 'dark' : 'light',
    colors: {
        bg: isDark ? '#0a0a0f' : '#f8fafc',
        bgSecondary: isDark ? '#111118' : '#ffffff',
        card: isDark ? '#16161f' : '#ffffff',
        cardHover: isDark ? '#1a1a24' : '#f1f5f9',
        surface: isDark ? '#1e1e28' : '#f1f5f9',
        border: isDark ? '#2a2a36' : '#e2e8f0',
        borderHover: isDark ? '#3a3a48' : '#cbd5e1',
        text: isDark ? '#f1f5f9' : '#0f172a',
        textSecondary: isDark ? '#94a3b8' : '#64748b',
        textMuted: isDark ? '#64748b' : '#94a3b8',
        primary: '#6366f1',
        primaryHover: '#4f46e5',
        primaryLight: isDark ? 'rgba(99, 102, 241, 0.15)' : 'rgba(99, 102, 241, 0.1)',
        secondary: '#8b5cf6',
        accent: '#06b6d4',
        success: '#10b981',
        successLight: isDark ? 'rgba(16, 185, 129, 0.15)' : 'rgba(16, 185, 129, 0.1)',
        warning: '#f59e0b',
        warningLight: isDark ? 'rgba(245, 158, 11, 0.15)' : 'rgba(245, 158, 11, 0.1)',
        error: '#ef4444',
        errorLight: isDark ? 'rgba(239, 68, 68, 0.15)' : 'rgba(239, 68, 68, 0.1)',
        gradient: isDark
            ? 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #06b6d4 100%)'
            : 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #06b6d4 100%)',
        gradientSubtle: isDark
            ? 'linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%)'
            : 'linear-gradient(135deg, rgba(99, 102, 241, 0.05) 0%, rgba(139, 92, 246, 0.05) 100%)',
    },
    shadows: {
        sm: isDark ? '0 1px 2px rgba(0, 0, 0, 0.5)' : '0 1px 2px rgba(0, 0, 0, 0.05)',
        md: isDark ? '0 4px 6px rgba(0, 0, 0, 0.5)' : '0 4px 6px rgba(0, 0, 0, 0.07)',
        lg: isDark ? '0 10px 15px rgba(0, 0, 0, 0.5)' : '0 10px 15px rgba(0, 0, 0, 0.1)',
        xl: isDark ? '0 20px 25px rgba(0, 0, 0, 0.6)' : '0 20px 25px rgba(0, 0, 0, 0.15)',
        glow: '0 0 40px rgba(99, 102, 241, 0.3)',
        glowSuccess: '0 0 30px rgba(16, 185, 129, 0.3)',
    },
    radius: {
        sm: '8px',
        md: '12px',
        lg: '16px',
        xl: '24px',
        full: '9999px',
    },
    transition: {
        fast: '150ms cubic-bezier(0.4, 0, 0.2, 1)',
        normal: '250ms cubic-bezier(0.4, 0, 0.2, 1)',
        slow: '350ms cubic-bezier(0.4, 0, 0.2, 1)',
        spring: '500ms cubic-bezier(0.34, 1.56, 0.64, 1)',
    }
});

// ============================================
// GLOBAL STYLES
// ============================================
const GlobalStyles = ({ theme }) => (
    <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        html {
            scroll-behavior: smooth;
        }
        
        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: ${theme.colors.bg};
            color: ${theme.colors.text};
            line-height: 1.6;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }

        ::selection {
            background: ${theme.colors.primary};
            color: white;
        }

        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }

        ::-webkit-scrollbar-track {
            background: ${theme.colors.surface};
        }

        ::-webkit-scrollbar-thumb {
            background: ${theme.colors.border};
            border-radius: 4px;
        }

        ::-webkit-scrollbar-thumb:hover {
            background: ${theme.colors.borderHover};
        }

        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }

        @keyframes slideIn {
            from { opacity: 0; transform: translateX(-20px); }
            to { opacity: 1; transform: translateX(0); }
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }

        @keyframes shimmer {
            0% { background-position: -1000px 0; }
            100% { background-position: 1000px 0; }
        }

        @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
        }

        @keyframes float {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-10px); }
        }

        .fade-in {
            animation: fadeIn 0.4s ease-out forwards;
        }

        .slide-in {
            animation: slideIn 0.3s ease-out forwards;
        }

        input:-webkit-autofill {
            -webkit-box-shadow: 0 0 0 1000px ${theme.colors.card} inset !important;
            -webkit-text-fill-color: ${theme.colors.text} !important;
        }
    `}</style>
);

// ============================================
// UI COMPONENTS
// ============================================
const ThemeContext = createContext(null);
const useTheme = () => useContext(ThemeContext);

const Button = ({ children, variant = 'primary', size = 'md', loading, disabled, fullWidth, icon, ...props }) => {
    const theme = useTheme();
    const variants = {
        primary: { bg: theme.colors.primary, bgHover: theme.colors.primaryHover, text: '#ffffff', border: 'transparent' },
        secondary: { bg: theme.colors.surface, bgHover: theme.colors.cardHover, text: theme.colors.text, border: theme.colors.border },
        ghost: { bg: 'transparent', bgHover: theme.colors.primaryLight, text: theme.colors.primary, border: 'transparent' },
        danger: { bg: theme.colors.error, bgHover: '#dc2626', text: '#ffffff', border: 'transparent' },
        success: { bg: theme.colors.success, bgHover: '#059669', text: '#ffffff', border: 'transparent' },
    };

    const sizes = {
        sm: { padding: '8px 16px', fontSize: '0.875rem', gap: '6px' },
        md: { padding: '12px 24px', fontSize: '1rem', gap: '8px' },
        lg: { padding: '16px 32px', fontSize: '1.125rem', gap: '10px' },
    };

    const v = variants[variant];
    const s = sizes[size];

    return (
        <button
            {...props}
            disabled={disabled || loading}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: s.gap,
                padding: s.padding,
                fontSize: s.fontSize,
                fontWeight: 600,
                background: v.bg,
                color: v.text,
                border: `1px solid ${v.border}`,
                borderRadius: theme.radius.lg,
                cursor: disabled || loading ? 'not-allowed' : 'pointer',
                opacity: disabled ? 0.5 : 1,
                transition: theme.transition.fast,
                width: fullWidth ? '100%' : 'auto',
                position: 'relative',
                overflow: 'hidden',
                ...props.style,
            }}
            onMouseEnter={(e) => {
                if (!disabled && !loading) {
                    e.currentTarget.style.background = v.bgHover;
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = theme.shadows.lg;
                }
            }}
            onMouseLeave={(e) => {
                e.currentTarget.style.background = v.bg;
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = 'none';
            }}
        >
            {loading && <span style={{ animation: 'spin 1s linear infinite', display: 'flex' }}>⏳</span>}
            {icon && !loading && <span>{icon}</span>}
            {children}
        </button>
    );
};

const Input = ({ label, error, icon, ...props }) => {
    const theme = useTheme();
    const [focused, setFocused] = useState(false);

    return (
        <div style={{ marginBottom: '20px' }}>
            {label && (
                <label style={{
                    display: 'block',
                    fontSize: '0.875rem',
                    fontWeight: 600,
                    color: theme.colors.text,
                    marginBottom: '8px',
                }}>
                    {label}
                </label>
            )}
            <div style={{ position: 'relative' }}>
                {icon && (
                    <span style={{
                        position: 'absolute',
                        left: '16px',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        color: theme.colors.textMuted,
                        fontSize: '1.1rem',
                    }}>
                        {icon}
                    </span>
                )}
                <input
                    {...props}
                    onFocus={(e) => { setFocused(true); props.onFocus?.(e); }}
                    onBlur={(e) => { setFocused(false); props.onBlur?.(e); }}
                    style={{
                        width: '100%',
                        padding: icon ? '14px 16px 14px 48px' : '14px 16px',
                        fontSize: '1rem',
                        background: theme.colors.card,
                        color: theme.colors.text,
                        border: `2px solid ${error ? theme.colors.error : focused ? theme.colors.primary : theme.colors.border}`,
                        borderRadius: theme.radius.md,
                        outline: 'none',
                        transition: theme.transition.fast,
                        ...props.style,
                    }}
                />
            </div>
            {error && <p style={{ fontSize: '0.875rem', color: theme.colors.error, marginTop: '6px' }}>{error}</p>}
        </div>
    );
};

const Card = ({ children, hover, glow, ...props }) => {
    const theme = useTheme();
    const [isHovered, setIsHovered] = useState(false);

    return (
        <div
            {...props}
            onMouseEnter={() => hover && setIsHovered(true)}
            onMouseLeave={() => hover && setIsHovered(false)}
            style={{
                background: theme.colors.card,
                borderRadius: theme.radius.xl,
                border: `1px solid ${isHovered ? theme.colors.borderHover : theme.colors.border}`,
                padding: '24px',
                transition: theme.transition.normal,
                transform: isHovered ? 'translateY(-4px)' : 'translateY(0)',
                boxShadow: glow && isHovered ? theme.shadows.glow : isHovered ? theme.shadows.lg : theme.shadows.sm,
                ...props.style,
            }}
        >
            {children}
        </div>
    );
};

const Badge = ({ children, variant = 'default', size = 'md', ...props }) => {
    const theme = useTheme();
    const variants = {
        default: { bg: theme.colors.surface, color: theme.colors.text },
        primary: { bg: theme.colors.primaryLight, color: theme.colors.primary },
        success: { bg: theme.colors.successLight, color: theme.colors.success },
        warning: { bg: theme.colors.warningLight, color: theme.colors.warning },
        error: { bg: theme.colors.errorLight, color: theme.colors.error },
    };

    const sizes = {
        sm: { padding: '4px 8px', fontSize: '0.75rem' },
        md: { padding: '6px 12px', fontSize: '0.875rem' },
        lg: { padding: '8px 16px', fontSize: '1rem' },
    };

    const v = variants[variant];
    const s = sizes[size];

    return (
        <span
            {...props}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: s.padding,
                fontSize: s.fontSize,
                fontWeight: 600,
                background: v.bg,
                color: v.color,
                borderRadius: theme.radius.full,
                ...props.style,
            }}
        >
            {children}
        </span>
    );
};

// ============================================
// AUTH MODAL
// ============================================
const AuthModal = ({ isOpen, onClose, initialMode = 'login' }) => {
    const theme = useTheme();
    const { login, register } = useAuth();
    const [mode, setMode] = useState(initialMode);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [formData, setFormData] = useState({
        email: '', username: '', password: '', confirmPassword: '', displayName: '', company: '', role: 'USER'
    });

    useEffect(() => { setMode(initialMode); }, [initialMode]);

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            if (mode === 'login') {
                await login(formData.email, formData.password);
            } else {
                if (formData.password !== formData.confirmPassword) {
                    throw new Error('Passwords do not match');
                }
                await register({
                    email: formData.email,
                    username: formData.username,
                    password: formData.password,
                    displayName: formData.displayName || formData.username,
                    company: formData.company,
                    role: formData.role,
                });
            }
            onClose();
        } catch (err) {
            setError(err.response?.data?.message || err.message || 'An error occurred');
        }
        setLoading(false);
    };

    return (
        <div
            style={{
                position: 'fixed',
                inset: 0,
                background: 'rgba(0, 0, 0, 0.7)',
                backdropFilter: 'blur(8px)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 1000,
                padding: '20px',
            }}
            onClick={onClose}
        >
            <Card
                onClick={(e) => e.stopPropagation()}
                style={{
                    width: '100%',
                    maxWidth: '440px',
                    maxHeight: '90vh',
                    overflow: 'auto',
                    animation: 'fadeIn 0.3s ease-out',
                    position: 'relative',
                }}
            >
                <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                    <div style={{ fontSize: '3rem', marginBottom: '16px', animation: 'float 3s ease-in-out infinite' }}>
                        🔐
                    </div>
                    <h2 style={{
                        fontSize: '1.75rem',
                        fontWeight: 700,
                        background: theme.colors.gradient,
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                        marginBottom: '8px',
                    }}>
                        {mode === 'login' ? 'Welcome Back' : 'Create Account'}
                    </h2>
                    <p style={{ color: theme.colors.textSecondary }}>
                        {mode === 'login' ? 'Sign in to your account' : 'Join the supply chain revolution'}
                    </p>
                </div>

                {error && (
                    <div style={{
                        background: theme.colors.errorLight,
                        color: theme.colors.error,
                        padding: '12px 16px',
                        borderRadius: theme.radius.md,
                        marginBottom: '20px',
                        fontSize: '0.875rem',
                    }}>
                        ❌ {error}
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    {mode === 'register' && (
                        <>
                            <Input label="Username" icon="👤" required placeholder="johndoe" value={formData.username}
                                onChange={(e) => setFormData({ ...formData, username: e.target.value })} />
                            <Input label="Display Name" icon="🏷️" placeholder="John Doe" value={formData.displayName}
                                onChange={(e) => setFormData({ ...formData, displayName: e.target.value })} />
                        </>
                    )}

                    <Input label="Email" icon="📧" type="email" required placeholder="john@example.com" value={formData.email}
                        onChange={(e) => setFormData({ ...formData, email: e.target.value })} />

                    <Input label="Password" icon="🔒" type="password" required placeholder="••••••••" value={formData.password}
                        onChange={(e) => setFormData({ ...formData, password: e.target.value })} />

                    {mode === 'register' && (
                        <>
                            <Input label="Confirm Password" icon="🔒" type="password" required placeholder="••••••••"
                                value={formData.confirmPassword}
                                onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })} />
                            <Input label="Company (Optional)" icon="🏢" placeholder="Acme Inc." value={formData.company}
                                onChange={(e) => setFormData({ ...formData, company: e.target.value })} />
                            <div style={{ marginBottom: '20px' }}>
                                <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '8px' }}>Role</label>
                                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                                    {['USER', 'MANUFACTURER', 'VERIFIER'].map((role) => (
                                        <button key={role} type="button" onClick={() => setFormData({ ...formData, role })}
                                            style={{
                                                padding: '10px 16px',
                                                fontSize: '0.875rem',
                                                fontWeight: 500,
                                                background: formData.role === role ? theme.colors.primary : theme.colors.surface,
                                                color: formData.role === role ? '#fff' : theme.colors.text,
                                                border: `1px solid ${formData.role === role ? theme.colors.primary : theme.colors.border}`,
                                                borderRadius: theme.radius.md,
                                                cursor: 'pointer',
                                                transition: theme.transition.fast,
                                            }}>
                                            {role === 'USER' && '👤'} {role === 'MANUFACTURER' && '🏭'} {role === 'VERIFIER' && '✓'}
                                            {' '}{role.charAt(0) + role.slice(1).toLowerCase()}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </>
                    )}

                    <Button type="submit" fullWidth loading={loading} style={{ marginTop: '8px' }}>
                        {mode === 'login' ? 'Sign In' : 'Create Account'}
                    </Button>
                </form>

                <div style={{ marginTop: '24px', paddingTop: '24px', borderTop: `1px solid ${theme.colors.border}`, textAlign: 'center' }}>
                    <p style={{ color: theme.colors.textSecondary, fontSize: '0.875rem' }}>
                        {mode === 'login' ? "Don't have an account? " : 'Already have an account? '}
                        <button type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}
                            style={{ background: 'none', border: 'none', color: theme.colors.primary, fontWeight: 600, cursor: 'pointer' }}>
                            {mode === 'login' ? 'Sign up' : 'Sign in'}
                        </button>
                    </p>
                </div>

                <button onClick={onClose} style={{
                    position: 'absolute', top: '16px', right: '16px', background: 'none', border: 'none',
                    color: theme.colors.textMuted, cursor: 'pointer', fontSize: '1.5rem', padding: '4px',
                }}>
                    ✕
                </button>
            </Card>
        </div>
    );
};

// ============================================
// MAIN APP CONTENT
// ============================================
const AppContent = () => {
    const theme = useTheme();
    const { user, logout, isAuthenticated } = useAuth();
    const [tab, setTab] = useState('register');
    const [showAuthModal, setShowAuthModal] = useState(false);
    const [authMode, setAuthMode] = useState('login');

    const [registerData, setRegisterData] = useState({ name: '', manufacturer: '', batch: '', description: '', expiry: '', location: '' });
    const [productId, setProductId] = useState('');
    const [registerResult, setRegisterResult] = useState(null);
    const [verifyResult, setVerifyResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [history, setHistory] = useState([]);
    const [requestTimes, setRequestTimes] = useState([]);
    const [metrics, setMetrics] = useState({ totalVerifications: 0, totalRegistrations: 0, averageResponseTime: 0, successRate: 100, apiStatus: 'checking' });

    const generateSerialNumber = useCallback((manufacturer, batch) => {
        const timestamp = Date.now().toString().slice(-6);
        const manufacturerCode = manufacturer.substring(0, 3).toUpperCase();
        return `${manufacturerCode}-${batch}-${timestamp}`;
    }, []);

    useEffect(() => {
        const successCount = history.filter(h => h.type === 'success').length;
        const totalCount = history.length;
        const verifyCount = history.filter(h => h.action.includes('Verif')).length;
        const registerCount = history.filter(h => h.action.includes('Register')).length;
        const avgTime = requestTimes.length > 0 ? Math.round(requestTimes.reduce((a, b) => a + b, 0) / requestTimes.length) : 0;
        setMetrics(prev => ({
            ...prev, totalVerifications: verifyCount, totalRegistrations: registerCount,
            averageResponseTime: avgTime, successRate: totalCount > 0 ? Math.round((successCount / totalCount) * 100) : 100,
        }));
    }, [history, requestTimes]);

    useEffect(() => {
        const checkHealth = async () => {
            try {
                const start = Date.now();
                await axios.get(`${API_BASE}/actuator/health`, { timeout: 5000 });
                setRequestTimes(prev => [...prev.slice(-19), Date.now() - start]);
                setMetrics(prev => ({ ...prev, apiStatus: 'connected' }));
            } catch {
                setMetrics(prev => ({ ...prev, apiStatus: 'disconnected' }));
            }
        };
        checkHealth();
        const interval = setInterval(checkHealth, 15000);
        return () => clearInterval(interval);
    }, []);

    const handleRegister = async (e) => {
        e.preventDefault();
        setLoading(true);
        setRegisterResult(null);
        const startTime = Date.now();
        try {
            const serialNumber = registerData.manufacturer && registerData.batch
                ? generateSerialNumber(registerData.manufacturer, registerData.batch) : registerData.batch;
            const res = await axios.post(PRODUCT_API_URL, {
                serialNumber, name: registerData.name, manufacturer: registerData.manufacturer, metadataUri: registerData.description || 'N/A'
            });
            const elapsed = Date.now() - startTime;
            setRequestTimes(prev => [...prev.slice(-19), elapsed]);
            setRegisterResult({ ...res.data, success: true, responseTime: elapsed });
            setHistory(prev => [{ action: 'Registered', data: res.data, time: new Date().toLocaleString(), type: 'success' }, ...prev]);
            setRegisterData({ name: '', manufacturer: '', batch: '', description: '', expiry: '', location: '' });
        } catch (err) {
            const elapsed = Date.now() - startTime;
            setRequestTimes(prev => [...prev.slice(-19), elapsed]);
            const errorMessage = err.response?.data?.message || 'Registration failed';
            setRegisterResult({ error: errorMessage, success: false });
            setHistory(prev => [{ action: 'Registration Failed', data: { error: errorMessage }, time: new Date().toLocaleString(), type: 'error' }, ...prev]);
        }
        setLoading(false);
    };

    const handleVerify = async (e) => {
        e.preventDefault();
        setLoading(true);
        setVerifyResult(null);
        const startTime = Date.now();
        try {
            const res = await axios.post(VERIFICATION_API_URL, { productSerialNumber: productId });
            const elapsed = Date.now() - startTime;
            setRequestTimes(prev => [...prev.slice(-19), elapsed]);
            setVerifyResult({ ...res.data, success: true, responseTime: elapsed });
            setHistory(prev => [{ action: 'Verified', data: res.data, time: new Date().toLocaleString(), type: 'success' }, ...prev]);
        } catch (err) {
            const elapsed = Date.now() - startTime;
            setRequestTimes(prev => [...prev.slice(-19), elapsed]);
            const errorMessage = err.response?.data?.message || 'Verification failed';
            setVerifyResult({ error: errorMessage, success: false });
            setHistory(prev => [{ action: 'Verification Failed', data: { error: errorMessage }, time: new Date().toLocaleString(), type: 'error' }, ...prev]);
        }
        setLoading(false);
    };

    const tabs = [
        { id: 'register', icon: '📝', label: 'Register' },
        { id: 'verify', icon: '🔍', label: 'Verify' },
        { id: 'history', icon: '📋', label: 'History' },
        { id: 'metrics', icon: '📊', label: 'Metrics' },
    ];

    const openAuth = (mode) => { setAuthMode(mode); setShowAuthModal(true); };

    return (
        <div style={{ minHeight: '100vh', background: theme.colors.bg, padding: '20px' }}>
            <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
                {/* Header */}
                <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '20px 0', marginBottom: '32px', flexWrap: 'wrap', gap: '16px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <div style={{ width: '48px', height: '48px', borderRadius: theme.radius.lg, background: theme.colors.gradient, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', boxShadow: theme.shadows.glow }}>🔗</div>
                        <div>
                            <h1 style={{ fontSize: '1.5rem', fontWeight: 700, background: theme.colors.gradient, WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>SupplyChain Auth</h1>
                            <p style={{ fontSize: '0.875rem', color: theme.colors.textSecondary }}>Blockchain-powered verification</p>
                        </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <Badge variant={metrics.apiStatus === 'connected' ? 'success' : 'error'}>
                            {metrics.apiStatus === 'connected' ? '🟢 Online' : '🔴 Offline'}
                        </Badge>
                        {isAuthenticated ? (
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                <div style={{ padding: '8px 16px', background: theme.colors.surface, borderRadius: theme.radius.full, display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <span style={{ fontSize: '1.25rem' }}>{user?.role === 'MANUFACTURER' ? '🏭' : user?.role === 'VERIFIER' ? '✓' : '👤'}</span>
                                    <span style={{ fontWeight: 500 }}>{user?.displayName || user?.username}</span>
                                </div>
                                <Button variant="ghost" size="sm" onClick={logout}>Sign Out</Button>
                            </div>
                        ) : (
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <Button variant="ghost" size="sm" onClick={() => openAuth('login')}>Sign In</Button>
                                <Button size="sm" onClick={() => openAuth('register')}>Get Started</Button>
                            </div>
                        )}
                    </div>
                </header>

                {/* Tabs */}
                <div style={{ display: 'flex', gap: '8px', marginBottom: '32px', padding: '8px', background: theme.colors.card, borderRadius: theme.radius.xl, border: `1px solid ${theme.colors.border}`, flexWrap: 'wrap' }}>
                    {tabs.map((t) => (
                        <button key={t.id} onClick={() => setTab(t.id)}
                            style={{
                                flex: 1, minWidth: '120px', padding: '14px 20px', fontSize: '0.95rem', fontWeight: 600,
                                background: tab === t.id ? theme.colors.primary : 'transparent',
                                color: tab === t.id ? '#fff' : theme.colors.textSecondary,
                                border: 'none', borderRadius: theme.radius.lg, cursor: 'pointer', transition: theme.transition.fast,
                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                            }}>
                            <span>{t.icon}</span>{t.label}
                        </button>
                    ))}
                </div>

                {/* Content */}
                <div className="fade-in" key={tab}>
                    {tab === 'register' && (
                        <Card>
                            <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '12px' }}>📝 Register New Product</h2>
                            <form onSubmit={handleRegister}>
                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
                                    <Input label="Product Name" icon="📦" required placeholder="e.g., Organic Coffee Beans" value={registerData.name} onChange={(e) => setRegisterData({ ...registerData, name: e.target.value })} />
                                    <Input label="Manufacturer" icon="🏭" required placeholder="e.g., Green Valley Co." value={registerData.manufacturer} onChange={(e) => setRegisterData({ ...registerData, manufacturer: e.target.value })} />
                                    <Input label="Batch Number" icon="🏷️" required placeholder="e.g., BATCH001" value={registerData.batch} onChange={(e) => setRegisterData({ ...registerData, batch: e.target.value })} />
                                    <Input label="Description (Optional)" icon="📝" placeholder="Product description" value={registerData.description} onChange={(e) => setRegisterData({ ...registerData, description: e.target.value })} />
                                </div>
                                <div style={{ marginTop: '24px' }}>
                                    <Button type="submit" loading={loading} icon="✨">Register Product</Button>
                                </div>
                            </form>
                            {registerResult && (
                                <div style={{ marginTop: '32px', padding: '24px', background: registerResult.success ? theme.colors.successLight : theme.colors.errorLight, borderRadius: theme.radius.lg, border: `1px solid ${registerResult.success ? theme.colors.success : theme.colors.error}` }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                                        <span style={{ fontSize: '2rem' }}>{registerResult.success ? '✅' : '❌'}</span>
                                        <h3 style={{ fontSize: '1.25rem', fontWeight: 600, color: registerResult.success ? theme.colors.success : theme.colors.error }}>
                                            {registerResult.success ? 'Product Registered Successfully!' : 'Registration Failed'}
                                        </h3>
                                    </div>
                                    {registerResult.success ? (
                                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
                                            <div><span style={{ color: theme.colors.textMuted, fontSize: '0.875rem' }}>Serial Number</span><p style={{ fontWeight: 600, fontFamily: 'monospace' }}>{registerResult.serialNumber}</p></div>
                                            <div><span style={{ color: theme.colors.textMuted, fontSize: '0.875rem' }}>Response Time</span><p style={{ fontWeight: 600 }}>{registerResult.responseTime}ms</p></div>
                                        </div>
                                    ) : <p style={{ color: theme.colors.error }}>{registerResult.error}</p>}
                                </div>
                            )}
                        </Card>
                    )}

                    {tab === 'verify' && (
                        <Card>
                            <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '12px' }}>🔍 Verify Product</h2>
                            <form onSubmit={handleVerify}>
                                <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
                                    <div style={{ flex: 1, minWidth: '280px' }}>
                                        <Input label="Product Serial Number" icon="🔎" required placeholder="Enter serial number to verify" value={productId} onChange={(e) => setProductId(e.target.value)} />
                                    </div>
                                    <Button type="submit" loading={loading} icon="🔍" style={{ marginBottom: '20px' }}>Verify Product</Button>
                                </div>
                            </form>
                            {verifyResult && (
                                <div style={{ marginTop: '24px', padding: '24px', background: verifyResult.success ? theme.colors.successLight : theme.colors.errorLight, borderRadius: theme.radius.lg, border: `1px solid ${verifyResult.success ? theme.colors.success : theme.colors.error}` }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                                        <span style={{ fontSize: '2rem' }}>{verifyResult.success && verifyResult.verified ? '✅' : verifyResult.success ? '⚠️' : '❌'}</span>
                                        <h3 style={{ fontSize: '1.25rem', fontWeight: 600, color: verifyResult.success ? theme.colors.success : theme.colors.error }}>
                                            {verifyResult.success && verifyResult.verified ? 'Product Verified - Authentic!' : verifyResult.success ? 'Product Found' : 'Verification Failed'}
                                        </h3>
                                    </div>
                                    {verifyResult.success ? (
                                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
                                            <div><span style={{ color: theme.colors.textMuted, fontSize: '0.875rem' }}>Product Name</span><p style={{ fontWeight: 600 }}>{verifyResult.productName}</p></div>
                                            <div><span style={{ color: theme.colors.textMuted, fontSize: '0.875rem' }}>Manufacturer</span><p style={{ fontWeight: 600 }}>{verifyResult.manufacturer}</p></div>
                                            <div><span style={{ color: theme.colors.textMuted, fontSize: '0.875rem' }}>Transaction Hash</span><p style={{ fontWeight: 600, fontFamily: 'monospace', fontSize: '0.75rem' }}>{verifyResult.transactionHash?.slice(0, 20)}...</p></div>
                                            <div><span style={{ color: theme.colors.textMuted, fontSize: '0.875rem' }}>Response Time</span><p style={{ fontWeight: 600 }}>{verifyResult.responseTime}ms</p></div>
                                        </div>
                                    ) : <p style={{ color: theme.colors.error }}>{verifyResult.error}</p>}
                                </div>
                            )}
                        </Card>
                    )}

                    {tab === 'history' && (
                        <Card>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                                <h2 style={{ fontSize: '1.5rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '12px' }}>📋 Activity History</h2>
                                {history.length > 0 && <Button variant="danger" size="sm" onClick={() => setHistory([])}>🗑️ Clear</Button>}
                            </div>
                            {history.length === 0 ? (
                                <div style={{ textAlign: 'center', padding: '60px 20px', color: theme.colors.textMuted }}>
                                    <div style={{ fontSize: '3rem', marginBottom: '16px' }}>📭</div>
                                    <p>No activity yet. Start by registering or verifying products!</p>
                                </div>
                            ) : (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                    {history.map((item, index) => (
                                        <div key={index} className="slide-in" style={{ padding: '16px', background: theme.colors.surface, borderRadius: theme.radius.lg, border: `1px solid ${theme.colors.border}`, animationDelay: `${index * 50}ms` }}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                                                <Badge variant={item.type === 'success' ? 'success' : 'error'}>{item.action}</Badge>
                                                <span style={{ fontSize: '0.875rem', color: theme.colors.textMuted }}>{item.time}</span>
                                            </div>
                                            <pre style={{ margin: 0, padding: '12px', background: theme.colors.card, borderRadius: theme.radius.md, fontSize: '0.8rem', overflow: 'auto', fontFamily: 'ui-monospace, monospace', color: theme.colors.text }}>
                                                {JSON.stringify(item.data, null, 2)}
                                            </pre>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </Card>
                    )}

                    {tab === 'metrics' && (
                        <div>
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px', marginBottom: '24px' }}>
                                {[
                                    { label: 'Total Registrations', value: metrics.totalRegistrations, icon: '📝', color: theme.colors.primary },
                                    { label: 'Total Verifications', value: metrics.totalVerifications, icon: '🔍', color: theme.colors.secondary },
                                    { label: 'Avg Response Time', value: `${metrics.averageResponseTime}ms`, icon: '⚡', color: theme.colors.accent },
                                    { label: 'Success Rate', value: `${metrics.successRate}%`, icon: '🎯', color: metrics.successRate >= 95 ? theme.colors.success : theme.colors.warning },
                                ].map((metric, index) => (
                                    <Card key={index} hover glow>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                            <div>
                                                <p style={{ fontSize: '0.875rem', color: theme.colors.textSecondary, marginBottom: '8px' }}>{metric.label}</p>
                                                <p style={{ fontSize: '2rem', fontWeight: 700, color: metric.color }}>{metric.value}</p>
                                            </div>
                                            <span style={{ fontSize: '2rem', opacity: 0.5 }}>{metric.icon}</span>
                                        </div>
                                    </Card>
                                ))}
                            </div>
                            <Card>
                                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '12px' }}>🖥️ System Status</h3>
                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px' }}>
                                    {[
                                        { name: 'API Gateway', status: metrics.apiStatus },
                                        { name: 'Product Service', status: 'connected' },
                                        { name: 'Verification Service', status: 'connected' },
                                        { name: 'PostgreSQL', status: 'connected' },
                                        { name: 'Redis Cache', status: 'connected' },
                                        { name: 'Blockchain RPC', status: 'connected' },
                                    ].map((service, index) => (
                                        <div key={index} style={{ padding: '16px', background: theme.colors.surface, borderRadius: theme.radius.md, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                            <span style={{ fontWeight: 500 }}>{service.name}</span>
                                            <Badge variant={service.status === 'connected' ? 'success' : 'error'} size="sm">
                                                {service.status === 'connected' ? '🟢' : '🔴'}
                                            </Badge>
                                        </div>
                                    ))}
                                </div>
                            </Card>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <footer style={{ marginTop: '48px', padding: '24px', textAlign: 'center', color: theme.colors.textMuted, fontSize: '0.875rem' }}>
                    <p>🔗 Powered by Blockchain • ⚡ Built with React • ☁️ Deployed on Vercel & Render</p>
                    <p style={{ marginTop: '8px', opacity: 0.7 }}>© {new Date().getFullYear()} SupplyChain Authentication Platform</p>
                </footer>
            </div>

            <AuthModal isOpen={showAuthModal} onClose={() => setShowAuthModal(false)} initialMode={authMode} />
        </div>
    );
};

// ============================================
// APP WRAPPER
// ============================================
function App() {
    const [darkMode, setDarkMode] = useState(() => {
        const saved = localStorage.getItem('theme');
        return saved ? saved === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
    });

    useEffect(() => { localStorage.setItem('theme', darkMode ? 'dark' : 'light'); }, [darkMode]);

    const theme = useMemo(() => createTheme(darkMode), [darkMode]);

    return (
        <ThemeContext.Provider value={theme}>
            <AuthProvider>
                <GlobalStyles theme={theme} />
                <AppContent />
                <button onClick={() => setDarkMode(!darkMode)}
                    style={{
                        position: 'fixed', bottom: '24px', right: '24px', width: '56px', height: '56px',
                        borderRadius: '50%', border: 'none', background: theme.colors.gradient, color: '#fff',
                        fontSize: '1.5rem', cursor: 'pointer', boxShadow: theme.shadows.lg, transition: theme.transition.spring,
                        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100,
                    }}
                    title={`Switch to ${darkMode ? 'light' : 'dark'} mode`}>
                    {darkMode ? '☀️' : '🌙'}
                </button>
            </AuthProvider>
        </ThemeContext.Provider>
    );
}

export default App;
