const root = document.documentElement
const body = document.body
const revealNodes = [...document.querySelectorAll("[data-reveal]")]
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)")
const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)")

body.classList.add("js-ready")

const showNode = node => node.classList.add("is-visible")

const revealAll = () => {
  revealNodes.forEach(showNode)
}

const revealInView = () => {
  revealNodes.forEach(node => {
    const rect = node.getBoundingClientRect()
    if (rect.top < window.innerHeight * 0.92) {
      showNode(node)
    }
  })
}

if ("IntersectionObserver" in window && !reduceMotion.matches) {
  const observer = new IntersectionObserver(
    entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) {
          return
        }
        showNode(entry.target)
        observer.unobserve(entry.target)
      })
    },
    {
      threshold: 0.16,
      rootMargin: "0px 0px -8% 0px"
    }
  )

  revealNodes.forEach((node, index) => {
    node.style.setProperty("--reveal-delay", `${Math.min(index * 70, 280)}ms`)
    observer.observe(node)
  })

  revealInView()
  window.addEventListener("load", revealInView, { once: true })
} else {
  revealAll()
}

if (finePointer.matches && !reduceMotion.matches) {
  let frame = 0

  const updatePointer = event => {
    if (frame) {
      return
    }

    const { clientX, clientY } = event

    frame = window.requestAnimationFrame(() => {
      root.style.setProperty("--pointer-x", ((clientX / window.innerWidth) * 100).toFixed(2))
      root.style.setProperty("--pointer-y", ((clientY / window.innerHeight) * 100).toFixed(2))
      frame = 0
    })
  }

  window.addEventListener("pointermove", updatePointer, { passive: true })
}
