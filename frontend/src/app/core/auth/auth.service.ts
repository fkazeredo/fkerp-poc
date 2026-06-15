import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

/**
 * Holds the access token in memory and talks to the auth endpoints. The refresh token lives in an
 * httpOnly cookie (sent automatically same-origin), so it is never handled by JS.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly accessToken = signal<string | null>(null);

  login(username: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/auth/login', { username, password }, { withCredentials: true })
      .pipe(tap((res) => this.accessToken.set(res.accessToken)));
  }

  refresh(): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/auth/refresh', {}, { withCredentials: true })
      .pipe(tap((res) => this.accessToken.set(res.accessToken)));
  }

  logout(): Observable<void> {
    this.accessToken.set(null);
    return this.http.post<void>('/api/auth/logout', {}, { withCredentials: true });
  }

  isAuthenticated(): boolean {
    return this.accessToken() !== null;
  }
}
