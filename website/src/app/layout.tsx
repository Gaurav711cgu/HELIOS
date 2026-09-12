import { Bricolage_Grotesque, Manrope, Space_Mono } from 'next/font/google';
import './globals.css';
import { Navbar } from '@/components/nav/Navbar';

const bricolage = Bricolage_Grotesque({
  subsets: ['latin'],
  weight: ['400', '700', '800'],
  variable: '--font-bricolage',
  display: 'swap',
});

const manrope = Manrope({
  subsets: ['latin'],
  weight: ['400', '500', '600'],
  variable: '--font-manrope',
  display: 'swap',
});

const spaceMono = Space_Mono({
  subsets: ['latin'],
  weight: ['400', '700'],
  variable: '--font-spacemono',
  display: 'swap',
});

export const metadata = {
  title: 'HELIOS Orchestrator',
  description: 'Java-native durable workflow engine — real-time execution metrics and fault simulation',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="en"
      className={`${bricolage.variable} ${manrope.variable} ${spaceMono.variable}`}
    >
      <body>
        <Navbar />
        {children}
      </body>
    </html>
  );
}
