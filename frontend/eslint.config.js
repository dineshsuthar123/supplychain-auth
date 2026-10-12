import js from '@eslint/js';
import ts from 'typescript-eslint';
import hooks from 'eslint-plugin-react-hooks';
export default ts.config(js.configs.recommended, ...ts.configs.recommended, {
  files: ['**/*.{ts,tsx}'],
  plugins: { 'react-hooks': hooks },
  rules: { ...hooks.configs.recommended.rules, '@typescript-eslint/no-explicit-any': 'error' },
  languageOptions: {
    globals: {
      window: 'readonly',
      document: 'readonly',
      navigator: 'readonly',
      fetch: 'readonly',
      URL: 'readonly',
      setTimeout: 'readonly',
      clearTimeout: 'readonly',
      AbortController: 'readonly',
      FormData: 'readonly',
      sessionStorage: 'readonly',
      localStorage: 'readonly',
    },
  },
});
