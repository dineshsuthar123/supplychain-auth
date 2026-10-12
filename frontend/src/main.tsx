import { createRoot } from 'react-dom/client';
import { Suspense } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './app/Auth';
import App from './app/App';
import '@fontsource-variable/dm-sans';
import '@fontsource-variable/fraunces';
import './styles/main.css';
import './styles/cinema.css';
const query = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15000,
      retry: (count, error) => count < 1 && (!('status' in error) || Number(error.status) >= 500),
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
});
createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={query}>
    <BrowserRouter>
      <AuthProvider>
        <Suspense
          fallback={
            <div role="status" className="loading">
              Opening Lotline…
            </div>
          }
        >
          <App />
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  </QueryClientProvider>,
);
