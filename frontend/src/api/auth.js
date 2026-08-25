import http from './http'

export function login(username, password) {
  return http.post('/auth/login', { username, password })
}

export function register(username, password, nickname) {
  return http.post('/auth/register', { username, password, nickname })
}

export function me() {
  return http.get('/auth/me')
}
