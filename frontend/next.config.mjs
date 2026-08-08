/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  images: {
    unoptimized: true,
  },
  async rewrites() {
    const apiOrigin = process.env.BACKEND_API_ORIGIN ?? "http://localhost:8080"
    return [
      {
        source: "/api/v1/:path*",
        destination: `${apiOrigin}/api/v1/:path*`,
      },
      {
        source: "/api/evidences/:path*",
        destination: `${apiOrigin}/api/evidences/:path*`,
      },
    ]
  },
}

export default nextConfig
