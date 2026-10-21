import { alpha, createTheme } from '@mui/material/styles'

/** Paleta de PixelVault: fondo azul noche con acentos violeta neón y cian. */
const colores = {
  fondo: '#0B0D17',
  superficie: '#141829',
  superficieAlta: '#1B2036',
  borde: 'rgba(148, 163, 209, 0.16)',
  violeta: '#8B5CF6',
  violetaClaro: '#A78BFA',
  cian: '#22D3EE',
  texto: '#E6E8F2',
  textoSecundario: '#9AA3C0',
}

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: colores.violetaClaro, dark: colores.violeta, contrastText: '#0B0D17' },
    secondary: { main: colores.cian, contrastText: '#0B0D17' },
    success: { main: '#34D399' },
    warning: { main: '#FBBF24' },
    error: { main: '#F87171' },
    info: { main: '#38BDF8' },
    background: { default: colores.fondo, paper: colores.superficie },
    text: { primary: colores.texto, secondary: colores.textoSecundario },
    divider: colores.borde,
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: 'Inter, "Segoe UI", Roboto, Arial, sans-serif',
    h4: { fontWeight: 700, letterSpacing: '-0.035em' },
    h5: { fontWeight: 700, letterSpacing: '-0.025em' },
    button: { fontWeight: 600, textTransform: 'none' },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: { body: { backgroundColor: colores.fondo } },
    },
    MuiPaper: {
      // Sin el degradado blanco que MUI aplica a las superficies en modo oscuro.
      styleOverrides: { root: { backgroundImage: 'none' } },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          borderRadius: 10,
          paddingInline: 18,
          variants: [
            {
              props: { variant: 'contained', color: 'primary' },
              style: {
                background: `linear-gradient(135deg, ${colores.violetaClaro}, ${colores.violeta})`,
                color: '#FFFFFF',
                '&:hover': {
                  background: `linear-gradient(135deg, #C4B5FD, ${colores.violetaClaro})`,
                },
              },
            },
          ],
        },
      },
    },
    MuiCard: {
      styleOverrides: { root: { border: `1px solid ${colores.borde}` } },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: alpha(colores.fondo, 0.85),
          backdropFilter: 'blur(10px)',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: { paper: { backgroundColor: '#0F1222' } },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          '&.Mui-selected': {
            background: `linear-gradient(90deg, ${alpha(colores.violeta, 0.32)}, ${alpha(colores.cian, 0.08)})`,
            color: colores.texto,
            '& .MuiListItemIcon-root': { color: colores.cian },
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: colores.borde },
        head: { color: colores.textoSecundario, backgroundColor: colores.superficieAlta },
      },
    },
    MuiDialog: {
      styleOverrides: { paper: { backgroundColor: colores.superficieAlta } },
    },
    MuiTab: {
      styleOverrides: { root: { '&.Mui-selected': { color: colores.cian } } },
    },
    MuiTabs: {
      styleOverrides: { indicator: { backgroundColor: colores.cian } },
    },
  },
})
