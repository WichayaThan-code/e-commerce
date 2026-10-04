import { useEffect, useState } from "react";
import { Outlet, useLocation, useNavigation } from "react-router-dom";

export default function RouteProgress() {
  const location = useLocation();
  const navigation = useNavigation();
  const [changing, setChanging] = useState(true);

  useEffect(() => {
    setChanging(true);
    const timer = window.setTimeout(() => setChanging(false), 520);
    return () => window.clearTimeout(timer);
  }, [location.pathname]);

  const loading = changing || navigation.state !== "idle";

  return (
    <>
      {loading && (
        <div className="route-progress" role="status" aria-live="polite">
          <span className="route-progress-bar" />
          <p>
            <i />
            กำลังโหลดข้อมูล
          </p>
        </div>
      )}
      <Outlet />
    </>
  );
}
