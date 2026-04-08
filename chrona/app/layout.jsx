import "./globals.css";

// ---- SEO Metadata ----
export const metadata = {
  title: "Chrona — Personal Mind Map",
  description:
    "An analog-inspired personal scheduling mind map. Pin your tasks, connect your goals, and design your life.",
};

// ---- Root Layout ----
export default function RootLayout({ children }) {
  return (
    <html lang="en" className="h-full antialiased">
      <head>
        {/* Google Fonts: Space Grotesk, Newsreader, Work Sans, Permanent Marker */}
        <link
          href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=Newsreader:ital,wght@0,300;0,400;0,500;0,600;0,700;1,300;1,400;1,500;1,600;1,700&family=Work+Sans:wght@300;400;500;600&family=Permanent+Marker&display=swap"
          rel="stylesheet"
        />
        {/* Material Symbols for icons */}
        <link
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
