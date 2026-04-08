# Chrona

Chrona is an analog-inspired personal scheduling mind map. It breaks away from traditional grid-based to-do lists and calendars, providing a visual, tactile canvas where your tasks, goals, and events are connected by dynamic red threads. 

Designed with an "analog archivist" aesthetic, the interface features paper textures, hand-drawn elements, and a modular mind-map structure, allowing you to curate your life rather than just manage it. This project is a pixel-perfect React/Next.js implementation of a UI design concept.

## Features

-   **Interactive Mind Map Canvas:** A fully pannable, expansive digital corkboard layout.
-   **Analog Aesthetic:** 
    -   Subtle paper grain and dot-grid textures.
    -   Hand-drawn aesthetic connecting lines ("red threads").
    -   Distinct "paper" styles for different categories (sticky notes, ruled paper, punched paper, torn edges).
-   **Modular Architecture:** Built with clean, reusable React components, making it highly extensible.
-   **Typography:** High-contrast mix of structured sans-serif, editorial serif, and hand-drawn fonts for a curated editorial feel.
-   **Micro-interactions:** Hover states mimic the physical shifting of paper cards.

## Tech Stack

*   **Framework:** [Next.js (v15+)](https://nextjs.org/)
*   **Styling:** [Tailwind CSS (v4)](https://tailwindcss.com/)
*   **Fonts:** Provided via `next/font/google` (Space Grotesk, Newsreader, Work Sans, Permanent Marker).
*   **Icons:** [Material Symbols Outlined](https://fonts.google.com/icons).

## Directory Structure
The architecture is designed for ease of debugging and modification. Every major UI element is isolated into its own file.

```
chrona/
├── app/
│   ├── globals.css              # Tailwind v4 theme tokens, textures, and custom CSS
│   ├── layout.jsx               # Root layout, loads fonts and icons
│   └── page.jsx                 # Main entry point; assembles the dashboard
├── components/
│   ├── CategoryNode.jsx         # The main branch nodes (e.g., Hackathon, Exam)
│   ├── CoreNode.jsx             # The central "ME" identity node
│   ├── FloatingActionButton.jsx # Bottom-right "+" action button
│   ├── Header.jsx               # Top navigation bar
│   ├── MindMapCanvas.jsx        # Pannable container managing the layout
│   ├── PaperGrain.jsx           # SVG noise overlay for paper texture
│   ├── RedThreads.jsx           # Dynamic SVG connection lines
│   ├── Sidebar.jsx              # Left navigation panel 
│   └── TaskCard.jsx             # Individual tasks that inherit category styling
├── hooks/
│   └── useCanvasPan.js          # Custom hook handling mouse-drag panning logic
└── lib/
    ├── data.js                  # Seed data containing exact positions for nodes
    └── types.js                 # Constants defining available category styles
```

## Setup and Development

1.  **Install dependencies:**
    ```bash
    npm install
    # or
    yarn install
    # or
    pnpm install
    ```

2.  **Start the development server:**
    ```bash
    npm run dev
    # or
    yarn dev
    # or
    pnpm dev
    ```

3.  **View the app:**
    Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

## Customization Guide

### Changing Data and Layout
The current state of the mind map (categories, tasks, and their positions) is hardcoded in `lib/data.js`. 
-  **Positions** are defined as percentages (`x`, `y`) relative to the `MindMapCanvas` container.
-  **Rotations** are defined in degrees (e.g., `-2`, `3`) to give the cards a natural, slightly messy look.

### Adding New Category Styles
If you want to add a new visual style for a category (e.g., a crumpled napkin or a polaroid photo):
1.  Define the new style string in `lib/types.js`.
2.  Add the corresponding CSS class logic in `components/CategoryNode.jsx` and `components/TaskCard.jsx`.
3.  Implement the CSS specifics in `app/globals.css`.

### Modifying the Canvas
The `MindMapCanvas` currently has a fixed inner height (`1000px`). If you add significantly more nodes, you may need to increase the dimensions of the inner container within `components/MindMapCanvas.jsx` to allow for a larger panning area.
