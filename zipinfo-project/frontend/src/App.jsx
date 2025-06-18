import "./App.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Layout from "./components/common/Layout";
import Main from "./components/Main";
import SalePage from "./components/sale/SalePage";
import StockPage from "./components/stock/StockPage";
import MyInfo from "./components/myPage/MyInfo";
import UpdateInfo from "./components/myPage/UpdateInfo";
import MemberLogin from "./components/member/MemberLogin";
import { MemberProvider } from "./components/member/MemberContext";
import MemberSignup from "./components/member/MemberSignup";

function App() {
  return (
    <BrowserRouter>
      <MemberProvider>
        <Routes>
          {/* 공통 레이아웃 라우트 */}
          <Route path="/" element={<Layout />}>
            <Route index element={<Main />} />
            <Route path="sale" element={<SalePage />} />
            <Route path="stock" element={<StockPage />} />
            <Route path="myPage" element={<MyInfo />} />
            <Route path="login" element={<MemberLogin />} />
            <Route path="signUp" element={<MemberSignup />} />

            <Route path="myPage/updateInfo" element={<UpdateInfo />} />
          </Route>
        </Routes>
      </MemberProvider>
    </BrowserRouter>
  );
}

export default App;
