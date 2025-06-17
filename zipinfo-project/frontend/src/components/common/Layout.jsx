import Header from "./Header";
import Footer from "./Footer";
import { Outlet } from "react-router-dom";

const Layout = () => {
  return (
    <>
      <Header />
      <main>
        <Outlet /> {/* 현재 페이지 컴포넌트가 여기에 표시됨 */}
      </main>
      <Footer />
    </>
  );
};

export default Layout;
