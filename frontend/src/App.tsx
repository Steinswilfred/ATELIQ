import { RouterProvider } from 'react-router-dom';
import { router } from './routes/routes';
import { ThemeProvider } from './theme/ThemeProvider';
import { Toaster } from './components/ui/Toaster';

export default function App() {
  return (
    <ThemeProvider>
      <RouterProvider router={router} />
      <Toaster />
    </ThemeProvider>
  );
}
