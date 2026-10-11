import { Navigate, Route, Routes } from "react-router-dom";
import { AnnouncementEditorScreen } from "../bindings/announcement-editor-screen";
import { AnnouncementScreen } from "../bindings/announcement-screen";
import { AnnouncementsScreen } from "../bindings/announcements-screen";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { announcementParameters, announcementsPath } from "../models/announcement-route";

export function CommunicationsRoutes(props: CommunicationsScreenProps) {
  return (
    <Routes>
      <Route path="announcements" element={<AnnouncementsScreen {...props} />} />
      <Route path="announcements/new" element={<AnnouncementEditorScreen {...props} creating />} />
      <Route
        path="announcements/:announcementId/edit"
        element={<AnnouncementEditorScreen {...props} creating={false} />}
      />
      <Route
        path="announcements/:announcementId/history"
        element={<AnnouncementsScreen {...props} history />}
      />
      <Route path="announcements/:announcementId" element={<AnnouncementScreen {...props} />} />
      <Route
        path="*"
        element={
          <Navigate
            to={`${announcementsPath}?${announcementParameters(props.access.companyId)}`}
            replace
          />
        }
      />
    </Routes>
  );
}
