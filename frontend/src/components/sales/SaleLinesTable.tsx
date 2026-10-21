import {
  Box,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ReferenceOption } from '../../api/types'
import { formatDecimal, formatMoney } from '../../utils/formatters'
import { referenceName } from '../../utils/references'
import { ratePercent, type RespuestaVenta } from './salesTypes'

interface SaleLinesTableProps {
  detalle: RespuestaVenta
  productos: ReferenceOption[]
  decimales?: number
}

/** Líneas y desglose de impuestos de una venta, en la moneda de la transacción. */
export function SaleLinesTable({ detalle, productos, decimales }: SaleLinesTableProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const { venta, lineas } = detalle
  const money = (amount: string | number) =>
    formatMoney(amount, venta.monedaCodigo, language, decimales)

  return (
    <Stack spacing={2}>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small" aria-label={t('sales.lines')}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>{t('sales.product')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('sales.warehouse')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('sales.quantity')}
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('sales.unitPrice')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('sales.tax')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('sales.taxAmount')}
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('sales.lineTotal')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {lineas.map((linea) => (
              <TableRow key={linea.id}>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {referenceName(productos, linea.productoId, linea.nombreProducto ?? linea.codigoProducto)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {linea.codigoProducto}
                  </Typography>
                </TableCell>
                <TableCell>{linea.bodegaNombre}</TableCell>
                <TableCell align="right">{linea.cantidad}</TableCell>
                <TableCell align="right">{money(linea.precioUnitario)}</TableCell>
                <TableCell>
                  {linea.nombreImpuestoAplicado} ({ratePercent(linea.tasaImpuestoAplicada, language)})
                </TableCell>
                <TableCell align="right">{money(linea.importeImpuesto)}</TableCell>
                <TableCell align="right">{money(linea.totalLinea)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>

      <Divider />
      <Box sx={{ display: 'grid', gridTemplateColumns: 'auto auto', justifyContent: 'end', columnGap: 4, rowGap: 0.5 }}>
        <Typography color="text.secondary">{t('sales.subtotal')}</Typography>
        <Typography align="right">{money(venta.subtotal)}</Typography>
        <Typography color="text.secondary">{t('sales.taxes')}</Typography>
        <Typography align="right">{money(venta.totalImpuestos)}</Typography>
        <Typography sx={{ fontWeight: 700 }}>{t('sales.total')}</Typography>
        <Typography align="right" sx={{ fontWeight: 700 }} data-testid="sale-total">
          {money(venta.total)}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('sales.exchangeRate')}
        </Typography>
        <Typography variant="body2" align="right" color="text.secondary">
          {formatDecimal(venta.tipoCambioAplicado, language)}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('sales.baseCurrencyTotal')}
        </Typography>
        <Typography variant="body2" align="right" color="text.secondary">
          {formatDecimal(venta.totalMonedaBase, language, 4)}
        </Typography>
      </Box>
    </Stack>
  )
}
