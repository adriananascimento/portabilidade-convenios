import { NavLink, Outlet } from 'react-router-dom'

export function BaseLayout() {
  return (
    <div className="estrutura">
      <a className="pular-conteudo" href="#conteudo">Pular para o conteúdo</a>
      <aside className="barra-lateral">
        <div className="marca">
          <span className="simbolo-marca" aria-hidden="true">P</span>
          <div>
            <span className="nome-marca">Portabilidade de Convênios</span>
          </div>
        </div>

        <nav aria-label="Menu principal">
          <p className="rotulo-menu">Gestão de portabilidade</p>
          <ul className="menu">
            <li>
              <NavLink to="/autorizacoes" className={({ isActive }) => `item-menu${isActive ? ' selecionado' : ''}`}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
                  <path d="M8 3H5v18h14V3h-3M8 2h8v4H8zM8 13l3 3 5-6" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                Autorizações
              </NavLink>
            </li>
            <li>
              <NavLink to="/operacoes" className={({ isActive }) => `item-menu${isActive ? ' selecionado' : ''}`}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
                  <path d="M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                Operações
              </NavLink>
            </li>
          </ul>
        </nav>
      </aside>

      <div className="area-principal">
        <header className="cabecalho">
          <span>Plataforma de Portabilidade</span>
        </header>
        <main id="conteudo" className="conteudo" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
