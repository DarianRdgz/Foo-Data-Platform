"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";

import {
  allLevelsFailed,
  mergeSearchResults,
  MIN_SEARCH_LENGTH,
  SEARCH_DEBOUNCE_MS,
  searchBetaAreas,
  type HeaderSearchOption,
} from "@/lib/header-search";

export default function HeaderSearch() {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [options, setOptions] = useState<HeaderSearchOption[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  const abortRef = useRef<AbortController | null>(null);
  const closeTimeoutRef = useRef<number | null>(null);
  const router = useRouter();

  function resetSearchUi() {
    setDebouncedQuery("");
    setOptions([]);
    setIsOpen(false);
    setIsLoading(false);
    setHasError(false);
    setActiveIndex(-1);
  }

  function closeDropdown() {
    setIsOpen(false);
    setActiveIndex(-1);
  }

  function cancelScheduledClose() {
    if (closeTimeoutRef.current !== null) {
      window.clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
  }

  function scheduleCloseDropdown() {
    closeTimeoutRef.current = window.setTimeout(() => {
      closeDropdown();
    }, 150);
  }

  function selectOption(option: HeaderSearchOption) {
    setQuery(option.title);
    closeDropdown();
    router.push(option.href);
  }

  function handleChange(event: React.ChangeEvent<HTMLInputElement>) {
    const nextQuery = event.target.value;
    const trimmed = nextQuery.trim();

    setQuery(nextQuery);

    if (trimmed.length < MIN_SEARCH_LENGTH) {
      abortRef.current?.abort();
      resetSearchUi();
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!isOpen) {
      return;
    }

    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        setActiveIndex((current) => Math.min(current + 1, options.length - 1));
        break;
      case "ArrowUp":
        event.preventDefault();
        setActiveIndex((current) => Math.max(current - 1, -1));
        break;
      case "Enter":
        event.preventDefault();
        if (activeIndex >= 0 && options[activeIndex]) {
          selectOption(options[activeIndex]);
        }
        break;
      case "Escape":
        event.preventDefault();
        closeDropdown();
        break;
      default:
        break;
    }
  }

  useEffect(() => {
    const trimmed = query.trim();

    if (trimmed.length < MIN_SEARCH_LENGTH) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      setHasError(false);
      setIsLoading(true);
      setActiveIndex(-1);
      setIsOpen(true);
      setDebouncedQuery(trimmed);
    }, SEARCH_DEBOUNCE_MS);

    return () => window.clearTimeout(timeoutId);
  }, [query]);

  useEffect(() => {
    if (!debouncedQuery) {
      return;
    }

    abortRef.current?.abort();

    const controller = new AbortController();
    abortRef.current = controller;

    searchBetaAreas(debouncedQuery, controller.signal)
      .then((levelResults) => {
        if (controller.signal.aborted) {
          return;
        }

        const merged = mergeSearchResults(levelResults);
        const failed = allLevelsFailed(levelResults);

        setOptions(merged);
        setHasError(failed && merged.length === 0);
        setIsOpen(true);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }

        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }

        setOptions([]);
        setHasError(true);
        setIsOpen(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [debouncedQuery]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();

      if (closeTimeoutRef.current !== null) {
        window.clearTimeout(closeTimeoutRef.current);
      }
    };
  }, []);

  const showDropdown =
    isOpen && (isLoading || hasError || options.length > 0);

  return (
    <div className="header-search" onMouseDown={cancelScheduledClose}>
      <input
        type="text"
        role="combobox"
        className="header-search-input"
        placeholder="Search states, counties, metros"
        aria-label="Search places"
        aria-expanded={showDropdown}
        aria-controls="header-search-listbox"
        aria-haspopup="listbox"
        aria-autocomplete="list"
        aria-activedescendant={
          activeIndex >= 0 ? `search-option-${activeIndex}` : undefined
        }
        value={query}
        onChange={handleChange}
        onFocus={() => {
          if (isLoading || hasError || options.length > 0) {
            setIsOpen(true);
          }
        }}
        onBlur={scheduleCloseDropdown}
        onKeyDown={handleKeyDown}
      />

      {showDropdown ? (
        <ul
          id="header-search-listbox"
          role="listbox"
          aria-label="Search results"
          className="header-search-dropdown"
        >
          {isLoading ? (
            <li className="header-search-status">Searching...</li>
          ) : hasError ? (
            <li className="header-search-status">
              Could not load search results right now.
            </li>
          ) : options.length === 0 ? (
            <li className="header-search-status">No matching places found.</li>
          ) : (
            options.map((option, index) => (
              <li
                key={option.key}
                id={`search-option-${index}`}
                role="option"
                aria-selected={index === activeIndex}
                className={`header-search-option${
                  index === activeIndex ? " header-search-option-active" : ""
                }`}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => selectOption(option)}
              >
                <span className="header-search-option-title">
                  {option.title}
                </span>
                <span className="header-search-option-meta">
                  {option.geoLevelLabel}
                  {option.stateContext ? ` · ${option.stateContext}` : ""}
                </span>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  );
}