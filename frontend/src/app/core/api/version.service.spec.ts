import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { VersionService } from './version.service';

describe('VersionService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('fetches the version from /api/version and exposes it', () => {
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(VersionService); // construction fires the GET
    const req = http.expectOne('/api/version');
    expect(req.request.method).toBe('GET');
    req.flush({ version: '0.13.0' });
    expect(service.version()).toBe('0.13.0');
    http.verify();
  });

  it('leaves the version empty when the request fails', () => {
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(VersionService);
    http.expectOne('/api/version').error(new ProgressEvent('error'));
    expect(service.version()).toBe('');
    http.verify();
  });
});
