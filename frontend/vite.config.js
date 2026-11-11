import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// 배포 전용 런타임을 일반 Vite로 바꾸고 원본의 @ 경로 별칭을 유지합니다.
export default defineConfig({
  plugins: [react()],
  server: {
    allowedHosts: ['terminal.local'],
    // 개발 서버: /api 요청을 Spring Boot(8080)로 전달합니다. TODO: 배포 시 API 주소 설정 필요
    proxy: { '/api': 'http://localhost:8080' },
  },
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
});
