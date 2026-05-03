import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TimeFormatProvider } from './context/TimeFormatContext'
import './i18n'
import './index.css'
import App from './App'
import { configureAuthInterceptor } from './api/tokenProvider'
import { axiosInstance } from './api/axiosInstance'

configureAuthInterceptor(axiosInstance)

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 60_000, refetchOnWindowFocus: false } },
})

const oidcConfig = {
  authority:
    (import.meta.env.VITE_OIDC_AUTHORITY as string | undefined) ??
    'http://localhost:8081/realms/kdiab',
  client_id:
    (import.meta.env.VITE_OIDC_CLIENT_ID as string | undefined) ?? 'kdiab-ui',
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  automaticSilentRenew: true,
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={queryClient}>
        <TimeFormatProvider>
          <App />
        </TimeFormatProvider>
      </QueryClientProvider>
    </AuthProvider>
  </StrictMode>,
)
