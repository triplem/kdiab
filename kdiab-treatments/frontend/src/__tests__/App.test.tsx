import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import App from '../App'
import { AuthProvider } from 'react-oidc-context'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { TimeFormatProvider } from '../context/TimeFormatContext'

const queryClient = new QueryClient()

describe('App Component', () => {
  it('renders correctly and requests authentication natively', () => {
    const oidcConfig = {
      authority: "http://localhost:8081/realms/kdiab-treatments",
      client_id: "test",
      redirect_uri: "http://localhost",
    };

    const { container, getByText } = render(
      <AuthProvider {...oidcConfig}>
        <QueryClientProvider client={queryClient}>
          <TimeFormatProvider>
            <App />
          </TimeFormatProvider>
        </QueryClientProvider>
      </AuthProvider>
    )

    expect(container).toBeDefined()
    // While loading auth, the app shows a loading state — not the main heading.
    expect(getByText(/Loading authentication/i)).toBeInTheDocument()
  })
})
