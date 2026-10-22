import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, expect } from 'vitest'
import * as axeMatchers from 'vitest-axe/matchers'
import { useAuthStore } from '../shared/session/useAuthStore'
import { resetFixtures } from './msw/handlers'
import { server } from './msw/server'

expect.extend(axeMatchers)

// O axe tenta usar canvas para medir contraste; o jsdom não implementa e só emitiria ruído.
HTMLCanvasElement.prototype.getContext = () => null

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetFixtures()
  useAuthStore.getState().logout('signed-out')
  useAuthStore.getState().clearNotice()
  sessionStorage.clear()
})

afterAll(() => {
  server.close()
})
