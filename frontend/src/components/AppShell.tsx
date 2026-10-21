import { useMemo, useState } from 'react'
import {
  AppBar,
  Box,
  Button,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import AssessmentRoundedIcon from '@mui/icons-material/AssessmentRounded'
import CategoryRoundedIcon from '@mui/icons-material/CategoryRounded'
import HomeRoundedIcon from '@mui/icons-material/HomeRounded'
import Inventory2RoundedIcon from '@mui/icons-material/Inventory2Rounded'
import LocalShippingRoundedIcon from '@mui/icons-material/LocalShippingRounded'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import MenuRoundedIcon from '@mui/icons-material/MenuRounded'
import PointOfSaleRoundedIcon from '@mui/icons-material/PointOfSaleRounded'
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded'
import ShoppingCartRoundedIcon from '@mui/icons-material/ShoppingCartRounded'
import { useTranslation } from 'react-i18next'
import type { AuthIdentity } from '../auth/identity'
import {
  crudEntities,
  crudEntityByName,
  userCanAccessCrudEntity,
  type CrudEntityMetadata,
} from '../crud/metadata'
import type { CrudEntityName } from '../generated/entidades'
import { navigationItems, visibleNavigation, type NavigationId } from '../navigation'
import { CatalogPage } from './catalog/CatalogPage'
import { CrudPage } from './CrudPage'
import { Dashboard } from './Dashboard'
import { InventoryPage } from './inventory/InventoryPage'
import { LanguageSwitcher } from './LanguageSwitcher'
import { PixelVaultLogo } from './PixelVaultLogo'
import { ReportsPage } from './reports/ReportsPage'
import { SalesPage } from './sales/SalesPage'
import { SettingsPage } from './settings/SettingsPage'

const drawerWidth = 256

const navigationIcons = {
  home: HomeRoundedIcon,
  catalog: CategoryRoundedIcon,
  inventory: Inventory2RoundedIcon,
  purchases: ShoppingCartRoundedIcon,
  suppliers: LocalShippingRoundedIcon,
  sales: PointOfSaleRoundedIcon,
  reports: AssessmentRoundedIcon,
  settings: SettingsRoundedIcon,
}

interface AppShellProps {
  user: AuthIdentity
  onLogout: () => Promise<void>
}

export function AppShell({ user, onLogout }: AppShellProps) {
  const { t } = useTranslation()
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('md'))
  const [mobileOpen, setMobileOpen] = useState(false)
  const [activeModule, setActiveModule] = useState<NavigationId>('home')
  const [activeEntity, setActiveEntity] = useState<CrudEntityMetadata | null>(null)
  const availableNavigation = useMemo(
    () => visibleNavigation(user.permisos),
    [user.permisos],
  )
  const visibleActiveModule = availableNavigation.some(
    (item) => item.id === activeModule,
  )
    ? activeModule
    : 'home'

  function selectModule(module: NavigationId) {
    setActiveModule(module)
    setActiveEntity(null)
    setMobileOpen(false)
  }

  function selectEntity(entity: CrudEntityMetadata) {
    setActiveEntity(entity)
    setMobileOpen(false)
  }

  function canOpenCrud(name: CrudEntityName) {
    return userCanAccessCrudEntity(crudEntityByName(name), user.permisos)
  }

  function openCrudByName(name: CrudEntityName) {
    const entity = crudEntityByName(name)
    if (userCanAccessCrudEntity(entity, user.permisos)) {
      setActiveEntity(entity)
    }
  }

  const drawer = (
    <Box className="drawer-content">
      <Toolbar />
      <Box className="drawer-user">
        <Typography variant="subtitle2" noWrap>
          {user.nombre}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap>
          {user.correo}
        </Typography>
      </Box>
      <Divider />
      <List component="nav" aria-label={t('app.name')} sx={{ px: 1.5, py: 1.5 }}>
        {availableNavigation.map((item) => {
          const Icon = navigationIcons[item.id]
          return (
            <ListItemButton
              key={item.id}
              selected={visibleActiveModule === item.id}
              onClick={() => selectModule(item.id)}
              aria-current={visibleActiveModule === item.id ? 'page' : undefined}
              sx={{ minHeight: 46, mb: 0.5 }}
            >
              <ListItemIcon>
                <Icon fontSize="small" />
              </ListItemIcon>
              <ListItemText primary={t(`nav.${item.id}`)} />
            </ListItemButton>
          )
        })}
      </List>
      <Box className="drawer-footer">
        <Typography variant="caption" color="text.secondary">
          {t('app.subtitle')}
        </Typography>
      </Box>
    </Box>
  )

  return (
    <Box className="app-shell">
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${drawerWidth}px)` },
          ml: { md: `${drawerWidth}px` },
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Toolbar sx={{ gap: 1.5 }}>
          {!desktop && (
            <IconButton
              edge="start"
              aria-label={t('common.openMenu')}
              onClick={() => setMobileOpen(true)}
            >
              <MenuRoundedIcon />
            </IconButton>
          )}
          <Box className="brand-title">
            <PixelVaultLogo size={30} />
            <Typography variant="subtitle1" noWrap className="brand-name">
              {t('app.name')}
            </Typography>
          </Box>
          <Box sx={{ flexGrow: 1 }} />
          <LanguageSwitcher />
          <Tooltip title={t('common.logout')}>
            <IconButton
              aria-label={t('common.logout')}
              color="primary"
              onClick={() => void onLogout()}
            >
              <LogoutRoundedIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Box component="nav" aria-label={t('app.name')}>
        <Drawer
          variant={desktop ? 'permanent' : 'temporary'}
          open={desktop || mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            width: drawerWidth,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: drawerWidth,
              boxSizing: 'border-box',
              borderRight: '1px solid',
              borderColor: 'divider',
            },
          }}
        >
          {drawer}
        </Drawer>
      </Box>

      <Box
        component="main"
        className="main-content"
        sx={{ width: { md: `calc(100% - ${drawerWidth}px)` } }}
      >
        <Toolbar />
        <Box className="page-container">
          {activeEntity ? (
            <CrudPage
              entity={activeEntity}
              permissions={user.permisos}
              onBack={() => setActiveEntity(null)}
            />
          ) : visibleActiveModule === 'home' ? (
            <Dashboard user={user} />
          ) : visibleActiveModule === 'catalog' ? (
            <CatalogPage
              permissions={user.permisos}
              canOpenCategories={canOpenCrud('categoria')}
              onOpenCrudEntity={openCrudByName}
            />
          ) : visibleActiveModule === 'inventory' ? (
            <InventoryPage
              permissions={user.permisos}
              roles={user.roles}
              canOpenWarehouses={canOpenCrud('bodega')}
              onOpenCrudEntity={openCrudByName}
            />
          ) : visibleActiveModule === 'sales' ? (
            <SalesPage permissions={user.permisos} onSelectEntity={selectEntity} />
          ) : visibleActiveModule === 'reports' ? (
            <ReportsPage />
          ) : visibleActiveModule === 'settings' ? (
            <SettingsPage permissions={user.permisos} onSelectEntity={selectEntity} />
          ) : (
            <ModulePlaceholder
              module={visibleActiveModule}
              permissions={user.permisos}
              onSelectEntity={selectEntity}
            />
          )}
        </Box>
      </Box>
    </Box>
  )
}

interface ModulePlaceholderProps {
  module: Exclude<NavigationId, 'home' | 'catalog' | 'inventory' | 'sales' | 'reports' | 'settings'>
  permissions: readonly string[]
  onSelectEntity: (entity: CrudEntityMetadata) => void
}

function ModulePlaceholder({
  module,
  permissions,
  onSelectEntity,
}: ModulePlaceholderProps) {
  const { t, i18n } = useTranslation()
  const config = navigationItems.find((item) => item.id === module)
  const entities = crudEntities.filter(
    (entity) =>
      entity.modulo === module && userCanAccessCrudEntity(entity, permissions),
  )
  if (!config) {
    return null
  }
  const Icon = navigationIcons[module]

  return (
    <Box className="module-placeholder">
      <Box className="card-icon card-icon-blue">
        <Icon color="primary" />
      </Box>
      <Typography variant="h4" sx={{ mt: 2 }}>
        {t(`modules.${module}.title`)}
      </Typography>
      <Typography color="text.secondary" sx={{ maxWidth: 600, mt: 1, mb: 2.5 }}>
        {t(`modules.${module}.description`)}
      </Typography>
      {entities.length > 0 ? (
        <Stack spacing={1.25}>
          {entities.map((entity) => (
            <Button
              key={entity.nombre}
              variant="outlined"
              sx={{ justifyContent: 'flex-start', width: 'fit-content' }}
              onClick={() => onSelectEntity(entity)}
            >
              {i18n.resolvedLanguage?.toLowerCase().startsWith('en')
                ? entity.etiquetas.en
                : entity.etiquetas.es}
            </Button>
          ))}
        </Stack>
      ) : (
        <Stack
          direction="row"
          spacing={1}
          className="coming-soon"
          sx={{ alignItems: 'center' }}
        >
          <Typography variant="body2" color="primary.main" sx={{ fontWeight: 600 }}>
            {t('modules.availableSoon')}
          </Typography>
        </Stack>
      )}
    </Box>
  )
}
