import SwiftUI
import UIKit

enum MediaPickerMode: Identifiable {
    case cameraPhoto
    case cameraVideo
    case library

    var id: String {
        switch self {
        case .cameraPhoto:
            return "cameraPhoto"
        case .cameraVideo:
            return "cameraVideo"
        case .library:
            return "library"
        }
    }

    var sourceType: UIImagePickerController.SourceType {
        switch self {
        case .cameraPhoto, .cameraVideo:
            return .camera
        case .library:
            return .photoLibrary
        }
    }

    var mediaTypes: [String] {
        switch self {
        case .cameraPhoto:
            return ["public.image"]
        case .cameraVideo:
            return ["public.movie"]
        case .library:
            return ["public.image", "public.movie"]
        }
    }

    var captureMode: UIImagePickerController.CameraCaptureMode? {
        switch self {
        case .cameraPhoto:
            return .photo
        case .cameraVideo:
            return .video
        case .library:
            return nil
        }
    }
}

struct MediaPicker: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss

    let mode: MediaPickerMode
    let onPick: (SelectedMedia) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.sourceType = mode.sourceType
        controller.mediaTypes = mode.mediaTypes
        if mode.sourceType == .camera, let captureMode = mode.captureMode {
            controller.cameraCaptureMode = captureMode
        }
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let parent: MediaPicker

        init(parent: MediaPicker) {
            self.parent = parent
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            defer { parent.dismiss() }
            if let image = info[.originalImage] as? UIImage {
                pickImage(image)
                return
            }
            if let url = info[.mediaURL] as? URL {
                pickVideo(url)
            }
        }

        private func pickImage(_ image: UIImage) {
            guard let data = image.jpegData(compressionQuality: 0.86) else {
                return
            }
            parent.onPick(
                SelectedMedia(
                    filename: "photo_\(Int(Date().timeIntervalSince1970)).jpg",
                    mimeType: "image/jpeg",
                    data: data
                )
            )
        }

        private func pickVideo(_ url: URL) {
            guard let data = try? Data(contentsOf: url) else {
                return
            }
            let ext = url.pathExtension.isEmpty ? "mov" : url.pathExtension
            let mimeType = ext.lowercased() == "mp4" ? "video/mp4" : "video/quicktime"
            parent.onPick(
                SelectedMedia(
                    filename: "video_\(Int(Date().timeIntervalSince1970)).\(ext)",
                    mimeType: mimeType,
                    data: data
                )
            )
        }
    }
}
